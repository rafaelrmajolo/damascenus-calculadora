import base64
import json
import os
import re
import threading
import tkinter as tk
from io import BytesIO
from tkinter import filedialog, messagebox
from tkinter import ttk

from PIL import Image, ImageTk
from openai import OpenAI

APP_TITLE = "DAMASCENUS - Calculadora de Confianca"
MODEL = os.getenv("OPENAI_MODEL", "gpt-5.6")

SYSTEM_PROMPT = r"""
Voce e o backoffice de analise do DAMASCENUS. Recebera um print de um bilhete de aposta esportiva.

OBJETIVO
1) Leia o bilhete pela imagem e identifique cada selecao, evento, mercado e odd.
2) Pesquise na web usando fontes atuais e confiaveis. Priorize: sites oficiais de ligas/torneios/clubes, ATP/WTA/FIFA/UEFA/CBF e equivalentes, provedores estatisticos reconhecidos, imprensa esportiva de alta reputacao e dados recentes de escalação/lesoes/forma.
3) Estime a probabilidade real de cada selecao ocorrer, em percentual de 0 a 100.
4) Compare a probabilidade estimada com o break-even da odd: break_even = 100 / odd.
5) Nao confunda odd baixa com aposta segura. Se os dados forem insuficientes ou contraditorios, reduza a estimativa e deixe isso claro.
6) Considere correlacao entre selecoes do mesmo jogo ao estimar o bilhete total.
7) Nao invente dados, resultados, escalacoes ou fontes.

CLASSIFICACAO VISUAL
- VERDE: margem >= +3,0 pontos percentuais sobre o break-even.
- AMARELO: margem entre 0,0 e +2,99 pp.
- VERMELHO: margem < 0 pp.

RETORNE APENAS JSON VALIDO, SEM MARKDOWN, NESTE FORMATO:
{
  "ticket_odd": 1.60,
  "ticket_probability": 66.0,
  "ticket_break_even": 62.5,
  "ticket_margin_pp": 3.5,
  "ticket_status": "VERDE|AMARELO|VERMELHO",
  "items": [
    {
      "event": "Time A x Time B",
      "market": "Mais de 0,5 gol",
      "odd": 1.20,
      "probability": 86.0,
      "break_even": 83.33,
      "margin_pp": 2.67,
      "status": "AMARELO|VERDE|VERMELHO",
      "reason": "frase curta"
    }
  ],
  "summary_lines": [
    "linha 1",
    "linha 2",
    "linha 3",
    "linha 4"
  ],
  "sources": ["nome curto da fonte 1", "nome curto da fonte 2"]
}

O texto em summary_lines deve ter no maximo 4 linhas, linguagem simples, direta e sem prometer green.
""".strip()


def data_url(path: str) -> str:
    ext = os.path.splitext(path)[1].lower().replace('.', '')
    mime = "jpeg" if ext in ("jpg", "jpeg") else ext or "png"
    with open(path, "rb") as f:
        raw = base64.b64encode(f.read()).decode("ascii")
    return f"data:image/{mime};base64,{raw}"


def extract_json(text: str):
    text = text.strip()
    try:
        return json.loads(text)
    except Exception:
        m = re.search(r"\{.*\}", text, re.S)
        if not m:
            raise ValueError("A resposta da IA nao veio em JSON valido.")
        return json.loads(m.group(0))


class BetAnalyzerApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_TITLE)
        self.geometry("860x720")
        self.minsize(760, 640)
        self.configure(bg="#0d1117")
        self.image_path = None
        self.preview_img = None
        self._build_style()
        self._build_ui()

    def _build_style(self):
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure("TFrame", background="#0d1117")
        style.configure("Card.TFrame", background="#161b22")
        style.configure("TLabel", background="#0d1117", foreground="#f0f6fc", font=("Segoe UI", 10))
        style.configure("Title.TLabel", background="#0d1117", foreground="#f0f6fc", font=("Segoe UI Semibold", 18))
        style.configure("Muted.TLabel", background="#0d1117", foreground="#8b949e", font=("Segoe UI", 9))
        style.configure("Card.TLabel", background="#161b22", foreground="#f0f6fc", font=("Segoe UI", 10))
        style.configure("Big.TLabel", background="#161b22", foreground="#f0f6fc", font=("Segoe UI Semibold", 24))
        style.configure("TButton", font=("Segoe UI Semibold", 10), padding=(12, 8))
        style.configure("Treeview", background="#161b22", fieldbackground="#161b22", foreground="#f0f6fc", rowheight=30, borderwidth=0)
        style.configure("Treeview.Heading", background="#21262d", foreground="#f0f6fc", font=("Segoe UI Semibold", 9))
        style.map("Treeview", background=[("selected", "#30363d")])

    def _build_ui(self):
        top = ttk.Frame(self)
        top.pack(fill="x", padx=20, pady=(18, 8))
        ttk.Label(top, text="DAMASCENUS", style="Title.TLabel").pack(side="left")
        ttk.Label(top, text="Calculadora de confianca de bilhetes", style="Muted.TLabel").pack(side="left", padx=(12, 0), pady=(7, 0))

        controls = ttk.Frame(self)
        controls.pack(fill="x", padx=20, pady=8)
        ttk.Button(controls, text="SUBIR PRINT", command=self.pick_image).pack(side="left")
        self.analyze_btn = ttk.Button(controls, text="ANALISAR", command=self.start_analysis)
        self.analyze_btn.pack(side="left", padx=8)
        self.status_lbl = ttk.Label(controls, text="Aguardando print...", style="Muted.TLabel")
        self.status_lbl.pack(side="left", padx=10)

        body = ttk.Frame(self)
        body.pack(fill="both", expand=True, padx=20, pady=8)

        left = ttk.Frame(body, style="Card.TFrame")
        left.pack(side="left", fill="both", expand=False, padx=(0, 10))
        left.configure(width=300)
        left.pack_propagate(False)

        self.preview_label = ttk.Label(left, text="Seu print aparece aqui", style="Card.TLabel", anchor="center")
        self.preview_label.pack(fill="both", expand=True, padx=10, pady=10)

        right = ttk.Frame(body)
        right.pack(side="left", fill="both", expand=True)

        summary_card = ttk.Frame(right, style="Card.TFrame")
        summary_card.pack(fill="x", pady=(0, 10))
        row = ttk.Frame(summary_card, style="Card.TFrame")
        row.pack(fill="x", padx=14, pady=12)

        self.ball = tk.Canvas(row, width=28, height=28, bg="#161b22", highlightthickness=0)
        self.ball.pack(side="left")
        self.ball_id = self.ball.create_oval(4, 4, 24, 24, fill="#6e7681", outline="")

        odds_box = ttk.Frame(row, style="Card.TFrame")
        odds_box.pack(side="left", padx=(10, 26))
        ttk.Label(odds_box, text="ODD", style="Card.TLabel").pack(anchor="w")
        self.odd_lbl = ttk.Label(odds_box, text="--", style="Big.TLabel")
        self.odd_lbl.pack(anchor="w")

        conf_box = ttk.Frame(row, style="Card.TFrame")
        conf_box.pack(side="left", padx=(0, 26))
        ttk.Label(conf_box, text="CONFIANCA ESTIMADA", style="Card.TLabel").pack(anchor="w")
        self.conf_lbl = ttk.Label(conf_box, text="--%", style="Big.TLabel")
        self.conf_lbl.pack(anchor="w")

        margin_box = ttk.Frame(row, style="Card.TFrame")
        margin_box.pack(side="left")
        ttk.Label(margin_box, text="MARGEM VS. BREAK-EVEN", style="Card.TLabel").pack(anchor="w")
        self.margin_lbl = ttk.Label(margin_box, text="-- pp", style="Big.TLabel")
        self.margin_lbl.pack(anchor="w")

        cols = ("status", "evento", "mercado", "odd", "prob", "be", "margem")
        self.tree = ttk.Treeview(right, columns=cols, show="headings", height=8)
        headings = {
            "status": "",
            "evento": "EVENTO",
            "mercado": "MERCADO",
            "odd": "ODD",
            "prob": "CONF.",
            "be": "B.E.",
            "margem": "MARGEM",
        }
        widths = {"status": 34, "evento": 150, "mercado": 160, "odd": 58, "prob": 65, "be": 65, "margem": 70}
        for c in cols:
            self.tree.heading(c, text=headings[c])
            self.tree.column(c, width=widths[c], anchor="center" if c not in ("evento", "mercado") else "w")
        self.tree.pack(fill="both", expand=True)

        text_card = ttk.Frame(right, style="Card.TFrame")
        text_card.pack(fill="x", pady=(10, 0))
        ttk.Label(text_card, text="LEITURA RAPIDA", style="Card.TLabel").pack(anchor="w", padx=12, pady=(10, 2))
        self.explain = tk.Text(text_card, height=5, wrap="word", bg="#161b22", fg="#f0f6fc", insertbackground="#f0f6fc", relief="flat", font=("Segoe UI", 10))
        self.explain.pack(fill="x", padx=10, pady=(0, 10))
        self.explain.configure(state="disabled")

    def pick_image(self):
        path = filedialog.askopenfilename(
            title="Selecione o print do bilhete",
            filetypes=[("Imagens", "*.png *.jpg *.jpeg *.webp"), ("Todos", "*.*")],
        )
        if not path:
            return
        self.image_path = path
        self._show_preview(path)
        self.status_lbl.config(text="Print carregado. Pronto para analisar.")

    def _show_preview(self, path):
        img = Image.open(path).convert("RGB")
        img.thumbnail((280, 520))
        self.preview_img = ImageTk.PhotoImage(img)
        self.preview_label.config(image=self.preview_img, text="")

    def start_analysis(self):
        if not self.image_path:
            messagebox.showwarning("DAMASCENUS", "Suba um print primeiro.")
            return
        if not os.getenv("OPENAI_API_KEY"):
            messagebox.showerror(
                "Chave da OpenAI",
                "Defina a variavel OPENAI_API_KEY antes de abrir o programa.\n\nVeja o README.txt.",
            )
            return
        self.analyze_btn.config(state="disabled")
        self.status_lbl.config(text="Lendo bilhete e pesquisando fontes atuais...")
        threading.Thread(target=self._analyze_worker, daemon=True).start()

    def _analyze_worker(self):
        try:
            client = OpenAI()
            response = client.responses.create(
                model=MODEL,
                tools=[{"type": "web_search"}],
                input=[
                    {
                        "role": "system",
                        "content": [{"type": "input_text", "text": SYSTEM_PROMPT}],
                    },
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": "Analise este bilhete agora. Use informacao atual e devolva somente o JSON solicitado.",
                            },
                            {
                                "type": "input_image",
                                "image_url": data_url(self.image_path),
                            },
                        ],
                    },
                ],
            )
            data = extract_json(response.output_text)
            self.after(0, lambda: self._render(data))
        except Exception as e:
            self.after(0, lambda: self._show_error(str(e)))

    def _show_error(self, msg):
        self.analyze_btn.config(state="normal")
        self.status_lbl.config(text="Falha na analise.")
        messagebox.showerror("DAMASCENUS", msg)

    def _status_color(self, status):
        s = str(status).upper()
        if s == "VERDE":
            return "#2ea043"
        if s == "AMARELO":
            return "#d29922"
        if s == "VERMELHO":
            return "#f85149"
        return "#6e7681"

    def _status_dot(self, status):
        s = str(status).upper()
        return {"VERDE": "🟢", "AMARELO": "🟡", "VERMELHO": "🔴"}.get(s, "⚪")

    def _render(self, data):
        self.analyze_btn.config(state="normal")
        self.status_lbl.config(text="Analise concluida.")

        odd = data.get("ticket_odd", 0)
        prob = data.get("ticket_probability", 0)
        margin = data.get("ticket_margin_pp", 0)
        status = data.get("ticket_status", "")

        self.odd_lbl.config(text=f"{float(odd):.2f}")
        self.conf_lbl.config(text=f"{float(prob):.1f}%")
        self.margin_lbl.config(text=f"{float(margin):+.1f} pp")
        self.ball.itemconfig(self.ball_id, fill=self._status_color(status))

        for row in self.tree.get_children():
            self.tree.delete(row)

        for item in data.get("items", []):
            self.tree.insert(
                "",
                "end",
                values=(
                    self._status_dot(item.get("status")),
                    item.get("event", ""),
                    item.get("market", ""),
                    f"{float(item.get('odd', 0)):.2f}",
                    f"{float(item.get('probability', 0)):.1f}%",
                    f"{float(item.get('break_even', 0)):.1f}%",
                    f"{float(item.get('margin_pp', 0)):+.1f} pp",
                ),
            )

        lines = data.get("summary_lines", [])[:4]
        if data.get("sources"):
            lines.append("Fontes: " + ", ".join(data.get("sources", [])[:3]))
        self.explain.configure(state="normal")
        self.explain.delete("1.0", "end")
        self.explain.insert("1.0", "\n".join(lines))
        self.explain.configure(state="disabled")


if __name__ == "__main__":
    BetAnalyzerApp().mainloop()
