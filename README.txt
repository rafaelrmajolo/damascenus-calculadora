DAMASCENUS - Calculadora de Confianca

O QUE FAZ
- Voce sobe um print do bilhete.
- O modelo le o print.
- Pesquisa informacoes atuais na web.
- Mostra odd, confianca estimada, break-even e margem.
- Verde: margem >= +3 pontos percentuais.
- Amarelo: margem entre 0 e +2,99 pp.
- Vermelho: margem negativa.
- Texto final curto, no maximo 4 linhas de explicacao, mais fontes.

IMPORTANTE
Este prototipo usa a OpenAI API como backoffice. Usar uma conversa comum do site ChatGPT como backend automatizado nao e uma interface programatica estavel. A API e o caminho adequado para automatizar imagem + pesquisa web + resposta estruturada.

COMO RODAR NO WINDOWS
1. Instale Python 3.11 ou mais recente.
2. Abra o Prompt de Comando nesta pasta.
3. Rode:
   pip install -r requirements.txt
4. Configure sua chave da OpenAI:
   set OPENAI_API_KEY=SUA_CHAVE_AQUI
5. Inicie:
   python app.py

MODELO
Por padrao usa gpt-5.6. Para trocar:
   set OPENAI_MODEL=gpt-5.6-terra

COMO GERAR EXE
Instale o PyInstaller:
   pip install pyinstaller
Depois:
   pyinstaller --noconsole --onefile --name DAMASCENUS_CALCULADORA app.py
O executavel ficara na pasta dist.

REGRA MATEMATICA
Break-even = 100 / odd.
Exemplo odd 1.60 -> 62,5%.
A classificacao compara a probabilidade estimada pelo modelo com esse ponto de equilibrio.

OBSERVACAO
Nenhum indicador garante resultado. A analise serve para estimar valor/risco e deve reduzir confianca quando houver dados insuficientes, correlacao ou informacao contraditoria.
