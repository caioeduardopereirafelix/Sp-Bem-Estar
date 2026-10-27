# Revisão jurídica antes de abrir o serviço

Os textos de [`termos.html`](../src/main/resources/static/termos.html) e [`privacidade.html`](../src/main/resources/static/privacidade.html) são um **rascunho técnico**. Eles descrevem com fidelidade o que o sistema faz, mas não foram escritos nem revisados por um profissional do direito. Antes de divulgar o endereço ao público, peça ao jurídico do órgão para conferir os pontos abaixo e ajustar o texto.

O que muda a cada instalação vem do `.env`: `PRIVACY_CONTROLLER` (o órgão responsável) e `PRIVACY_CONTACT` (o contato do encarregado). As duas páginas leem esses valores de `GET /api/v1/privacy`.

## LGPD

1. **Base legal.** O texto cita a execução de políticas públicas pelo poder público (art. 7º, III, e art. 23). Confirmar se é a base adequada para cada tratamento, inclusive as notificações push, que dependem de a pessoa ligá-las.
2. **Encarregado (DPO).** O art. 41 exige identidade e contato do encarregado publicados. Definir quem é e preencher `PRIVACY_CONTACT`.
3. **Crianças e adolescentes (art. 14).** O serviço não pede idade. Decidir se precisa de aviso ou restrição para menores de 18 anos.
4. **Transferência internacional (art. 33).** Recebem o IP do visitante:
   - OpenFreeMap (mapa);
   - cdnjs/Cloudflare (bibliotecas);
   - os serviços de push do Google, da Mozilla, da Apple e da Microsoft, para quem liga os avisos.

   Avaliar se o texto atual basta ou se é preciso mais (cláusulas, consentimento, alternativa nacional).
5. **Prazos de guarda.** O texto diz "enquanto forem úteis" para relatos e fotos. Verificar se a tabela de temporalidade do órgão pede um prazo definido.
6. **Direitos do titular (art. 18).** O cidadão é anônimo. O pedido de exclusão é feito pelo número do chamado. Confirmar se o procedimento é aceitável e quem responde e em quanto tempo.
7. **Planilha exportada.** Ela sai do sistema, embora sem dados de quem relatou. Definir quem pode exportar, para onde ela vai e por quanto tempo fica guardada fora do sistema. Cada exportação fica no log com o operador que a fez.

## Fotos e direito de imagem

8. **Fotos do cidadão.** Os termos pedem que a foto não mostre rostos, placas ou o interior de casas, mas o sistema não detecta isso sozinho. Definir:
   - o que a moderação faz ao encontrar uma foto assim;
   - em quanto tempo atende um pedido de remoção vindo de quem aparece na foto.
9. **Licença de uso.** Os termos dizem que quem envia autoriza, sem custo, a publicação e o uso pelo órgão. Confirmar a redação e o alcance (estatísticas, prestação de contas, divulgação).
10. **Foto do conserto.** Ela é tirada por servidores e também é pública. Orientar a equipe: só o local, sem pessoas nem placas. Avaliar se é preciso uma norma interna.

## Marco Civil e serviço público

11. **Registros de acesso (art. 15 da Lei 12.965/2014).** O sistema não guarda IP. Ele fica só na memória, por até uma hora, para o limite de envios. O art. 15 obriga apenas provedores com fins econômicos; confirmar se um órgão público está dispensado ou se precisa guardar os registros por 6 meses.
12. **Lei 13.460/2017 (usuário do serviço público).** Verificar se o canal precisa dizer prazos de resposta, ligar-se à ouvidoria ou publicar uma carta de serviços.
13. **Não é canal de emergência.** Os termos listam 190, 192, 193 e 199. Confirmar os números e se há um canal municipal a citar (por exemplo, o 156).

## Termos de uso

14. **Responsabilidade.** Os termos dizem que relatar não garante prazo nem resultado e que o serviço pode ficar fora do ar. Revisar a redação com o jurídico.
15. **Foro e legislação.** O texto cita as leis aplicáveis, mas não define foro. Incluir se o órgão costuma definir.
16. **Aceite.** O aceite é implícito, pelo aviso "Ao enviar, você concorda com os termos de uso" junto do botão de envio. Confirmar se basta ou se precisa de uma caixa de marcar.

Depois da revisão, atualize a data no alto das duas páginas.
