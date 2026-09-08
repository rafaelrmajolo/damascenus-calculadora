package br.com.damascenus.calculadora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1001;
    private static final String PREFS = "damascenus_prefs";
    private static final String PREF_KEY = "openai_api_key";
    private static final String PREF_MODEL = "openai_model";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Uri selectedUri;

    private ImageView preview;
    private Button analyzeButton;
    private TextView statusText;
    private TextView oddText;
    private TextView confidenceText;
    private TextView breakEvenText;
    private TextView marginText;
    private View statusBall;
    private LinearLayout itemsContainer;
    private TextView summaryText;
    private ProgressBar progress;

    private static final String SYSTEM_PROMPT =
            "Voce e o backoffice de analise do DAMASCENUS. Recebera um print de um bilhete de aposta esportiva.\n\n" +
            "OBJETIVO\n" +
            "1) Leia o bilhete pela imagem e identifique cada selecao, evento, mercado e odd.\n" +
            "2) Pesquise na web usando fontes atuais e confiaveis. Priorize sites oficiais de ligas, torneios e clubes; ATP/WTA/FIFA/UEFA/CBF e equivalentes; provedores estatisticos reconhecidos; imprensa esportiva de alta reputacao; escalações, lesoes e forma recente.\n" +
            "3) Estime a probabilidade real de cada selecao ocorrer, de 0 a 100.\n" +
            "4) Compare a probabilidade estimada com o break-even da odd: break_even = 100 / odd.\n" +
            "5) Nao confunda odd baixa com aposta segura. Se os dados forem insuficientes ou contraditorios, reduza a confianca e deixe claro.\n" +
            "6) Considere correlacao entre selecoes do mesmo jogo ao estimar o bilhete total.\n" +
            "7) Nao invente dados, resultados, escalacoes ou fontes.\n\n" +
            "CLASSIFICACAO\n" +
            "VERDE: margem >= +3,0 pontos percentuais sobre o break-even.\n" +
            "AMARELO: margem entre 0,0 e +2,99 pp.\n" +
            "VERMELHO: margem < 0 pp.\n\n" +
            "Retorne APENAS JSON valido, sem markdown, exatamente neste formato:\n" +
            "{\n" +
            "  \"ticket_odd\": 1.60,\n" +
            "  \"ticket_probability\": 66.0,\n" +
            "  \"ticket_break_even\": 62.5,\n" +
            "  \"ticket_margin_pp\": 3.5,\n" +
            "  \"ticket_status\": \"VERDE|AMARELO|VERMELHO\",\n" +
            "  \"items\": [{\"event\":\"Time A x Time B\",\"market\":\"Mais de 0,5 gol\",\"odd\":1.20,\"probability\":86.0,\"break_even\":83.33,\"margin_pp\":2.67,\"status\":\"AMARELO|VERDE|VERMELHO\",\"reason\":\"frase curta\"}],\n" +
            "  \"summary_lines\": [\"linha 1\",\"linha 2\",\"linha 3\",\"linha 4\"],\n" +
            "  \"sources\": [\"fonte 1\",\"fonte 2\"]\n" +
            "}\n" +
            "summary_lines deve ter no maximo 4 linhas, em portugues simples, direto e sem prometer green.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        if (prefs.getString(PREF_KEY, "").trim().isEmpty()) {
            showApiKeyDialog();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp((int) radiusDp));
        return g;
    }

    private void buildUi() {
        final int BG = Color.rgb(13, 17, 23);
        final int CARD = Color.rgb(22, 27, 34);
        final int WHITE = Color.rgb(240, 246, 252);
        final int MUTED = Color.rgb(139, 148, 158);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("DAMASCENUS", 24, WHITE, true);
        TextView sub = label("  Calculadora de confiança", 13, MUTED, false);
        header.addView(title);
        header.addView(sub);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, dp(10));

        Button pick = new Button(this);
        pick.setText("SUBIR PRINT");
        pick.setOnClickListener(v -> pickImage());
        actions.addView(pick, new LinearLayout.LayoutParams(0, dp(52), 1f));

        analyzeButton = new Button(this);
        analyzeButton.setText("ANALISAR");
        analyzeButton.setEnabled(false);
        analyzeButton.setOnClickListener(v -> analyze());
        LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        abp.setMargins(dp(8), 0, 0, 0);
        actions.addView(analyzeButton, abp);
        root.addView(actions);

        Button settingsBtn = new Button(this);
        settingsBtn.setText("CONFIGURAR CHAVE DA OPENAI");
        settingsBtn.setOnClickListener(v -> showApiKeyDialog());
        root.addView(settingsBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        statusText = label("Aguardando print...", 13, MUTED, false);
        statusText.setPadding(0, dp(8), 0, dp(10));
        root.addView(statusText);

        FrameLayout previewCard = new FrameLayout(this);
        previewCard.setBackground(rounded(CARD, 14));
        previewCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setAdjustViewBounds(true);
        preview.setMinimumHeight(dp(180));
        previewCard.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        root.addView(previewCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(276)));

        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        summaryCard.setBackground(rounded(CARD, 14));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scp.setMargins(0, dp(12), 0, 0);
        root.addView(summaryCard, scp);

        LinearLayout headline = new LinearLayout(this);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        statusBall = new View(this);
        statusBall.setBackground(rounded(Color.rgb(110, 118, 129), 100));
        headline.addView(statusBall, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView resultTitle = label("  RESULTADO DO BILHETE", 15, WHITE, true);
        headline.addView(resultTitle);
        summaryCard.addView(headline);

        HorizontalScrollView statsScroll = new HorizontalScrollView(this);
        statsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, 0);
        oddText = statBlock(stats, "ODD", "--", WHITE);
        confidenceText = statBlock(stats, "CONFIANÇA", "--%", WHITE);
        breakEvenText = statBlock(stats, "BREAK-EVEN", "--%", WHITE);
        marginText = statBlock(stats, "MARGEM", "-- pp", WHITE);
        statsScroll.addView(stats);
        summaryCard.addView(statsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView itemsHeader = label("ENTRADAS", 13, MUTED, true);
        itemsHeader.setPadding(0, dp(18), 0, dp(8));
        root.addView(itemsHeader);
        itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(itemsContainer);

        LinearLayout readCard = new LinearLayout(this);
        readCard.setOrientation(LinearLayout.VERTICAL);
        readCard.setPadding(dp(14), dp(12), dp(14), dp(14));
        readCard.setBackground(rounded(CARD, 14));
        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rcp.setMargins(0, dp(12), 0, 0);
        root.addView(readCard, rcp);
        readCard.addView(label("LEITURA RÁPIDA", 13, MUTED, true));
        summaryText = label("Suba um print para analisar.", 15, WHITE, false);
        summaryText.setPadding(0, dp(8), 0, 0);
        summaryText.setLineSpacing(0, 1.12f);
        readCard.addView(summaryText);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42));
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.setMargins(0, dp(14), 0, 0);
        root.addView(progress, pp);

        setContentView(scroll);
    }

    private TextView statBlock(LinearLayout parent, String title, String value, int valueColor) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 0, dp(28), 0);
        block.addView(label(title, 11, Color.rgb(139, 148, 158), true));
        TextView valueView = label(value, 24, valueColor, true);
        block.addView(valueView);
        parent.addView(block);
        return valueView;
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            preview.setImageURI(selectedUri);
            analyzeButton.setEnabled(true);
            statusText.setText("Print carregado. Pronto para analisar.");
        }
    }

    private void showApiKeyDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);

        EditText key = new EditText(this);
        key.setHint("sk-...");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString(PREF_KEY, ""));
        box.addView(label("Chave da OpenAI", 13, Color.DKGRAY, true));
        box.addView(key);

        EditText model = new EditText(this);
        model.setSingleLine(true);
        model.setText(prefs.getString(PREF_MODEL, "gpt-5.6"));
        box.addView(label("Modelo", 13, Color.DKGRAY, true));
        box.addView(model);

        new AlertDialog.Builder(this)
                .setTitle("Backoffice DAMASCENUS")
                .setMessage("A chave fica salva apenas neste aparelho. Para uso pessoal. Não distribua um APK com chave embutida.")
                .setView(box)
                .setPositiveButton("SALVAR", (d, which) -> {
                    prefs.edit().putString(PREF_KEY, key.getText().toString().trim())
                            .putString(PREF_MODEL, model.getText().toString().trim().isEmpty() ? "gpt-5.6" : model.getText().toString().trim())
                            .apply();
                    Toast.makeText(this, "Configuração salva.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void analyze() {
        if (selectedUri == null) {
            Toast.makeText(this, "Suba um print primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String apiKey = prefs.getString(PREF_KEY, "").trim();
        if (apiKey.isEmpty()) {
            showApiKeyDialog();
            return;
        }
        analyzeButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Lendo bilhete, pesquisando fontes e calculando margem...");
        itemsContainer.removeAllViews();
        summaryText.setText("Analisando...");

        executor.submit(() -> {
            try {
                String imageDataUrl = imageToDataUrl(selectedUri);
                JSONObject result = callOpenAi(apiKey, prefs.getString(PREF_MODEL, "gpt-5.6"), imageDataUrl);
                runOnUiThread(() -> renderResult(result));
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    analyzeButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    statusText.setText("Falha na análise.");
                    new AlertDialog.Builder(this)
                            .setTitle("Erro")
                            .setMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private String imageToDataUrl(Uri uri) throws Exception {
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new Exception("Não consegui abrir essa imagem.");
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int max = 1600;
        if (Math.max(w, h) > max) {
            float scale = max / (float)Math.max(w, h);
            bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(w * scale), Math.round(h * scale), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out);
        String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        return "data:image/jpeg;base64," + b64;
    }

    private JSONObject callOpenAi(String apiKey, String model, String imageDataUrl) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray tools = new JSONArray();
        tools.put(new JSONObject().put("type", "web_search"));
        body.put("tools", tools);

        JSONArray input = new JSONArray();
        input.put(new JSONObject()
                .put("role", "system")
                .put("content", new JSONArray().put(new JSONObject().put("type", "input_text").put("text", SYSTEM_PROMPT))));
        JSONArray userContent = new JSONArray();
        userContent.put(new JSONObject().put("type", "input_text").put("text", "Analise somente o bilhete deste print. Pesquise informações atuais antes de estimar a probabilidade."));
        userContent.put(new JSONObject().put("type", "input_image").put("image_url", imageDataUrl));
        input.put(new JSONObject().put("role", "user").put("content", userContent));
        body.put("input", input);

        URL url = new URL("https://api.openai.com/v1/responses");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(30000);
        con.setReadTimeout(120000);
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "Bearer " + apiKey);
        con.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = con.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String raw = readAll(stream);
        if (code < 200 || code >= 300) {
            String msg = raw;
            try {
                JSONObject err = new JSONObject(raw).optJSONObject("error");
                if (err != null) msg = err.optString("message", raw);
            } catch (Exception ignored) {}
            throw new Exception("OpenAI retornou HTTP " + code + ": " + msg);
        }

        JSONObject response = new JSONObject(raw);
        String outputText = extractOutputText(response);
        if (outputText == null || outputText.trim().isEmpty()) throw new Exception("A IA não devolveu texto de análise.");
        return parseJsonObject(outputText);
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String extractOutputText(JSONObject response) {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) return direct;
        JSONArray output = response.optJSONArray("output");
        if (output == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject c = content.optJSONObject(j);
                if (c == null) continue;
                String text = c.optString("text", "");
                if (!text.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    private JSONObject parseJsonObject(String text) throws Exception {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (firstNl >= 0 && last > firstNl) t = t.substring(firstNl + 1, last).trim();
        }
        try {
            return new JSONObject(t);
        } catch (Exception e) {
            int a = t.indexOf('{');
            int b = t.lastIndexOf('}');
            if (a >= 0 && b > a) return new JSONObject(t.substring(a, b + 1));
            throw new Exception("A resposta da IA não veio em JSON válido.");
        }
    }

    private void renderResult(JSONObject data) {
        analyzeButton.setEnabled(true);
        progress.setVisibility(View.GONE);

        double odd = data.optDouble("ticket_odd", 0);
        double prob = data.optDouble("ticket_probability", 0);
        double be = data.optDouble("ticket_break_even", odd > 0 ? 100.0 / odd : 0);
        double margin = data.optDouble("ticket_margin_pp", prob - be);
        String status = data.optString("ticket_status", statusFromMargin(margin));

        oddText.setText(odd > 0 ? String.format(Locale.US, "%.2f", odd) : "--");
        confidenceText.setText(String.format(Locale.US, "%.1f%%", prob));
        breakEvenText.setText(String.format(Locale.US, "%.1f%%", be));
        marginText.setText(String.format(Locale.US, "%+.1f pp", margin));
        int color = colorForStatus(status);
        statusBall.setBackground(rounded(color, 100));
        marginText.setTextColor(color);
        statusText.setText(status + " • análise concluída");

        itemsContainer.removeAllViews();
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) addItemCard(item);
            }
        }

        JSONArray lines = data.optJSONArray("summary_lines");
        StringBuilder summary = new StringBuilder();
        if (lines != null) {
            int max = Math.min(4, lines.length());
            for (int i = 0; i < max; i++) {
                String line = lines.optString(i, "").trim();
                if (!line.isEmpty()) {
                    if (summary.length() > 0) summary.append('\n');
                    summary.append(line);
                }
            }
        }
        summaryText.setText(summary.length() > 0 ? summary.toString() : "Sem resumo disponível.");
    }

    private void addItemCard(JSONObject item) {
        final int CARD = Color.rgb(22, 27, 34);
        final int WHITE = Color.rgb(240, 246, 252);
        final int MUTED = Color.rgb(139, 148, 158);
        String st = item.optString("status", statusFromMargin(item.optDouble("margin_pp", -999)));
        int color = colorForStatus(st);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(CARD, 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dp(8));
        itemsContainer.addView(card, cp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(rounded(color, 100));
        top.addView(dot, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView event = label("  " + item.optString("event", "Evento"), 14, WHITE, true);
        top.addView(event, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView odds = label(String.format(Locale.US, "%.2f", item.optDouble("odd", 0)), 16, WHITE, true);
        top.addView(odds);
        card.addView(top);

        TextView market = label(item.optString("market", ""), 13, WHITE, false);
        market.setPadding(dp(22), dp(5), 0, 0);
        card.addView(market);
        String stats = String.format(Locale.US, "Conf. %.1f%%  •  B.E. %.1f%%  •  Margem %+.1f pp",
                item.optDouble("probability", 0), item.optDouble("break_even", 0), item.optDouble("margin_pp", 0));
        TextView small = label(stats, 12, MUTED, false);
        small.setPadding(dp(22), dp(4), 0, 0);
        card.addView(small);
        String reason = item.optString("reason", "").trim();
        if (!reason.isEmpty()) {
            TextView r = label(reason, 12, MUTED, false);
            r.setPadding(dp(22), dp(4), 0, 0);
            card.addView(r);
        }
    }

    private String statusFromMargin(double margin) {
        if (margin >= 3.0) return "VERDE";
        if (margin >= 0.0) return "AMARELO";
        return "VERMELHO";
    }

    private int colorForStatus(String status) {
        if ("VERDE".equalsIgnoreCase(status)) return Color.rgb(46, 160, 67);
        if ("AMARELO".equalsIgnoreCase(status)) return Color.rgb(210, 153, 34);
        return Color.rgb(248, 81, 73);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
