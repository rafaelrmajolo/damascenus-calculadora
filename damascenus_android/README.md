# DAMASCENUS - Calculadora Android

Aplicativo Android nativo para analisar prints de bilhetes esportivos usando a OpenAI Responses API com pesquisa web.

## O que faz
- Seleciona print pela galeria/arquivos.
- Envia o print para análise multimodal.
- Usa pesquisa web como ferramenta da análise.
- Extrai odd total e entradas.
- Calcula/mostra break-even, probabilidade estimada e margem.
- Sinaliza: verde (>= +3 pp), amarelo (0 a +2,99 pp), vermelho (< 0 pp).
- Mostra resumo em no máximo 4 linhas.

## Configuração no app
Na primeira abertura, informe sua `OPENAI_API_KEY` e o modelo. A chave fica salva somente no aparelho via SharedPreferences.

> Para um app distribuído publicamente, não é recomendado usar chave pessoal no cliente. O ideal é um backend próprio que proteja a chave.

## Compilar no Android Studio
Abra a pasta raiz no Android Studio e rode `assembleDebug` ou use Run.

APK de debug esperado:
`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
O workflow `.github/workflows/build-apk.yml` compila automaticamente e publica o APK como artifact de cada execução.
