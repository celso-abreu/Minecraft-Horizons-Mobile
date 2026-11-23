# Plano de portabilidade do Horizon para Android

## Conclusão executiva

A portabilidade é viável sem reescrever o jogo em Java: o cliente, a simulação, os mundos e o renderer já são majoritariamente Rust. A base do renderer usa `wgpu`, com Vulkan e GLES habilitados, e o código já contém lógica Android para teclado, gamepads e controles touch.

O repositório ainda **não tem um APK validado**. Já existe um host Android NativeActivity, uma Activity de importação por SAF, configuração Gradle/NDK para arm64 e uma primeira ligação dos controles touch ao loop do jogo. Essas alterações são experimentais: não passaram por compilação Rust/Java/Gradle, instalação nem teste em dispositivo. Ciclo de vida, perfis gráficos móveis e compatibilidade real de drivers seguem sem validação.

A rota continua sendo manter o motor Rust e uma camada Android fina, em vez de portar o launcher desktop inteiro. O MVP deve ser ARM64, offline e de jogador único; importa um pacote preparado pelo usuário a partir da própria cópia de Minecraft Java Edition. O APK não deve incluir arquivos proprietários. Multiplayer autenticado, descoberta LAN, mods e funcionalidades do launcher ficam para fases posteriores.

## Implementação em andamento (23/11/2026)

- `android/native-host` fornece a entrada `android_main`, escolhe `filesDir/game` como diretório corrente e registra as partes oficiais antes de iniciar o cliente Rust.
- `android/app` oferece importação ZIP pelo seletor do Android, verifica o manifesto `26.3`/schema 7 e instala os recursos no armazenamento privado. Os arquivos de Gradle compilam o host Rust via NDK para `arm64-v8a`.
- `WindowEvent::Touch` agora tem um caminho experimental até `TouchControls`, estado virtual de gamepad, movimento da câmera, raycast de toque, HUD, menus e interações touch no inventário.
- A validação feita até agora é sintática/estática: `git diff --check`, parser Tree-sitter de Rust/Java/Groovy, TOML e XML passaram. Nenhum `cargo check`, APK, `adb` ou aparelho foi usado; Rust/Cargo, JDK, Gradle, Android SDK/NDK e `adb` não estão instalados neste ambiente.

## Estado real da base de código

| Área | O que já existe | Lacuna para Android |
|---|---|---|
| Cliente e simulação | O cliente, o mundo, o protocolo e o renderer são implementados em Rust. Há binário de jogo, launcher desktop e um host `android_main` em `android/native-host`. | O host ainda não foi compilado; caminho de inicialização, argumentos, mods, stack e arquivos precisam de validação no alvo Android. |
| Janela e eventos | O loop usa `winit` e `ApplicationHandler`; há manifesto, Activity de setup e configuração Gradle/NDK para NativeActivity arm64. | O fluxo de superfície, suspensão/retomada, orientação e ciclo de vida ainda não foi validado em aparelho. |
| Renderização | O renderer usa `wgpu`; Vulkan e GLES são dependências habilitadas para plataformas não macOS. A ordem automática atual para plataformas não Windows tenta Vulkan e depois OpenGL/GLES. Os shaders e recursos de GPU são amplos e já têm vários testes de backend. | Não há matriz de testes Android, seleção de perfil móvel, validação de memória térmica ou garantia de que cada formato/limite de GPU esteja disponível em todos os aparelhos. |
| Touch | `horizon-client-render` contém `TouchControls`, gestos, joystick/D-pad, mira, botões, HUD, layouts e botões customizados. A integração experimental cobre eventos, estado virtual de gamepad, câmera/raycast, menus e inventário. | Não há build nem teste do caminho integrado; ainda faltam IME/teclado virtual, retorno, ciclo de vida e validação de ergonomia em aparelho. |
| Teclado e gamepad Android | Há decodificação Android no upstream e o touch passa pelo estado virtual de controle. | Teclado, IME e gamepads físicos não foram conectados/validados como caminho móvel; não presumir que `gilrs` seja backend Android. |
| Assets do Minecraft | `horizon-prepare` continua sendo a etapa desktop; `SetupActivity` importa ZIP via SAF, valida versão/schema e copia para `filesDir/game/.local/horizon-client/26.3`. | O layout de ZIP foi implementado para a pasta de saída, mas o fluxo real de preparação/extração e o carregamento pelo jogo ainda precisam de teste. |
| Saves e arquivos | O host define `filesDir/game` como diretório corrente, e o jogo usa esse caminho para dados Android. | A persistência de mundos, disco insuficiente, cópia/backup e exportação SAF não foram testados; não há fluxo de importação/exportação de mundos ainda. |
| Launcher e contas | O launcher desktop gerencia instalações, contas, preparação, atualizações e servidores usando UI desktop, diretórios do sistema, armazenamento de credenciais e subprocessos. | Não é uma camada móvel reutilizável. Android precisa de uma UI pequena própria, autenticação compatível com navegador/deep link e armazenamento de segredos via Android Keystore. |

## MVP Android mínimo

### Escopo

- Aplicativo Android arm64 (`arm64-v8a`) com uma única janela de jogo, em orientação retrato ou paisagem.
- Inicialização do cliente Rust dentro de uma Activity nativa, primeiro usando o caminho NativeActivity já previsto pela dependência Android de `winit`.
- Renderer `wgpu`, Vulkan como primeira opção e GLES como fallback; sem exigir shaders de terceiros ou recursos gráficos opcionais.
- Importação, por seletor de documentos do Android, de um pacote de recursos produzido a partir da cópia licenciada do jogador. O pacote é validado e copiado para o armazenamento privado do app.
- Criação, carregamento e gravação de mundos locais no armazenamento privado. Importação e exportação de mundos por ação explícita do usuário.
- Controles touch básicos: mover, olhar, atacar/minerar, usar/colocar, pular, agachar, selecionar hotbar, abrir inventário e pausar.
- Teclado virtual e IME para chat e campos de texto; botão Voltar tratado conforme a tela ativa, sem roubar eventos do teclado virtual.
- Perfil gráfico inicial conservador, por exemplo: distância de renderização e simulação reduzida, escala de resolução menor que 100% em aparelhos modestos, limite de quadros configurável e sem HDR, ray tracing, frame generation ou packs de shader.

### Fora do MVP

- Login Microsoft, entrada autenticada em servidores, convites, relay e reconexão automática.
- Descoberta de servidores LAN e hospedagem de mundo a partir do telefone.
- Launcher completo, gestão de múltiplas versões/instâncias, atualização de executáveis e compilação de mods nativos.
- Instalação e execução de mods que dependem de subprocessos, editor completo de controles customizados e configurações avançadas de desktop.
- Acesso amplo ao armazenamento externo. Não solicitar permissão de armazenamento total.

### Critérios globais de aceite do MVP

1. O APK instala e inicia em um aparelho arm64 real; a primeira tela é renderizada por `wgpu` sem depender de um servidor local ou do computador de desenvolvimento.
2. A seleção/importação de recursos e de mundo usa o seletor do Android e não depende de caminhos de outras aplicações.
3. O usuário consegue abrir um mundo local, jogar com touch, salvar, fechar, reabrir e confirmar que o mundo permanece íntegro.
4. O jogo suspende renderização e trabalho não essencial em segundo plano, restaura a superfície ao voltar e não deixa botões ou teclas presos.
5. O perfil gráfico mantém a meta de quadros escolhida para o aparelho de referência em uma cena de teste reproduzível, sem falhas por falta de memória e sem aquecimento crescente que force queda contínua de desempenho.
6. O aplicativo não distribui JARs, assets ou outros arquivos do Minecraft; informa claramente que os recursos devem vir da cópia do usuário.

## Decisões técnicas

### Renderização: `wgpu`, Vulkan e GLES

| Opção | Decisão | Justificativa e trabalho necessário |
|---|---|---|
| `wgpu` | Manter como API do renderer. | O código existente já concentra recursos, superfícies, pipelines e shaders atrás de `wgpu`; trocar para outra API duplicaria uma parte extensa do renderer e aumentaria o risco de divergência. |
| Vulkan | Backend preferido em `Auto`, quando o driver e a superfície forem válidos. | A ordem atual já tenta Vulkan primeiro nas plataformas não Windows. Validar criação de superfície Android, swapchain, formatos, perda/recriação de dispositivo e comportamento nos drivers móveis. |
| GLES/EGL | Fallback explícito e testado. | A dependência GLES já está habilitada. O fallback deve lidar com limites menores, formatos diferentes, extensões e recursos de shader não disponíveis. Não presumir que a presença de GLES implique suporte a todas as capacidades do renderer. |
| Vulkan ou GLES nativos fora de `wgpu` | Não usar no MVP. | Uma implementação paralela exigiria duplicar o backend, os recursos e a tradução de shaders. Só reconsiderar se testes em aparelhos identificarem uma limitação incontornável de `wgpu`. |

O perfil Android precisa negociar capacidades antes de criar recursos: limites de textura e camadas, formatos de profundidade e render target, suporte a alvos float, buffers de armazenamento, modos de apresentação e memória disponível. Recursos opcionais do renderer devem ter fallback ou ficar desligados no perfil móvel. O modo de recuperação deve cobrir falha de criação, perda de dispositivo, retomada do app e mudança de orientação.

A tela gráfica deve oferecer um perfil móvel simples, não apenas expor todas as opções desktop. Os valores atuais de partida são orientados a desktop: 12 chunks de renderização e simulação, escala de resolução de 100% e alvo de 60 FPS; o modo de bateria existente reduz o limite de quadros, mas não substitui a pausa de renderização nem o controle de carga. Como hipótese inicial a medir, oferecer um perfil baixo com 6 chunks, escala de resolução de 75% e limite de 30 FPS, e um perfil superior que possa chegar a 60 FPS se o aparelho sustentar. Reusar o controle de escala dinâmica depois de validado. Ray tracing, mesh shaders, HDR, geração de quadros, packs de shader e distância LOD extrema devem ser desativados por padrão e habilitados somente após medição em hardware compatível.

### Controles touch, HUD, inventário e UI responsiva

A implementação touch deve entrar no loop de eventos e alimentar os mecanismos de entrada que o jogo já consome:

1. Converter cada evento de toque em coordenadas relativas à área útil da janela, levando em conta escala de UI, densidade, orientação e recortes da tela.
2. Preservar o identificador de cada dedo durante toda a interação; não reduzir todos os dedos a um único cursor do mouse.
3. Em jogo, encaminhar gestos para `TouchControls`: movimento, mira, taps, mineração/uso por pressão longa, botões auxiliares e múltiplas ações simultâneas. Ler o estado a cada tick e liberá-lo em cancelamento, perda de foco ou suspensão.
4. Desenhar os controles como uma camada do HUD. O código já tem desenho e posições normalizadas; ligar esse desenho ao frame do jogo e ao estado de interface.
5. Em menus, telas e inventário, mapear toque para o modelo de ponteiro já existente, sem encaminhar o mesmo toque também como controle de jogo. Testar toque simples, arraste de item, divisão de pilha, shift-click, tooltip, receitas e troca de telas.
6. Adicionar uma tela de edição de layout: arrastar, redimensionar, alterar opacidade/tamanho, alternar mão esquerda, escolher esquema e restaurar padrões. A estrutura de configurações já modela modos, layouts e botões customizados; falta a UI e o salvamento integrado.
7. Tratar teclado virtual e IME em campos de texto. Enquanto o IME estiver ativo, o botão Voltar deve primeiro fechar o teclado; depois pode fechar a tela ou abrir o menu de pausa.

O primeiro esquema deve manter poucos controles sempre visíveis e evitar sobreposição com hotbar, HUD, notificações do sistema e recortes. A UI deve recalcular layouts ao girar a tela e funcionar em proporções de telefone, tablet e dispositivo dobrável. A experiência de inventário deve ser tratada como requisito de jogo, não apenas como um clique convertido para mouse.

### Arquivos, saves, assets e dados do jogo

| Dado | Estratégia Android |
|---|---|
| Diretório do app | Receber do host Android um caminho absoluto em `filesDir` e usá-lo como raiz explícita do jogo. Evitar depender do diretório corrente, `HOME`, descoberta de instalações desktop ou caminhos calculados pelo launcher. |
| Recursos do Minecraft | No MVP, importar um pacote de recursos já preparado e com manifesto de versão/integridade. Copiar para armazenamento privado em arquivo temporário, validar e só então promovê-lo ao diretório ativo. Não incluir JARs nem assets do Minecraft no APK. |
| Preparação no aparelho | Fase posterior. Separar a descoberta/cópia de arquivos da geração de dados. `horizon-prepare` executa ferramentas Java; a execução direta desse processo não é uma premissa válida para Android. Para preparação integral no aparelho, será preciso substituir/portar esse passo de geração, ou introduzir um mecanismo Android compatível e suportado. |
| Saves | Guardar saves ativos no armazenamento privado e continuar usando os formatos de mundo existentes. Para importar/exportar, usar o Storage Access Framework e copiar o conteúdo para/da raiz interna; não passar URI de conteúdo diretamente às rotinas que esperam `Path`, seek e locks de arquivo. |
| Configuração, cache e logs | Permanecer no espaço privado do app, com quotas e limpeza controladas. Exportar logs ou capturas somente por ação explícita do usuário. |
| Permissões | Não pedir acesso amplo a arquivos. O seletor de documentos concede acesso apenas aos itens escolhidos. A permissão de rede deve ser adicionada quando o multiplayer entrar no escopo; permissões de descoberta local só quando a descoberta LAN for implementada. |

Para proteger os saves contra encerramento do processo ou falta de energia, testar a gravação atual em armazenamento interno real e evitar mover diretamente o fluxo de gravação para provedores SAF. A importação/exportação deve ser transacional, reportar falta de espaço e não substituir um mundo existente sem confirmação e possibilidade de recuperação.

### Compilação Rust e empacotamento

- Primeiro alvo de aparelho: `aarch64-linux-android`, empacotado como ABI `arm64-v8a`. Instalar o target Rust correspondente; apontar `ANDROID_NDK_HOME` para o NDK e `CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER` para o linker Clang do target (por exemplo, `aarch64-linux-android<API>-clang`); configurar também `CC`/`AR` do target para build scripts de dependências nativas. A compilação de distribuição é a do wrapper Android, não apenas o binário desktop com outro target.
- O cliente já escolhe a dependência Android de `winit` com `android-native-activity`; isso não cria sozinho uma Activity, uma biblioteca nativa inicializável nem um APK.
- Criar um wrapper Android pequeno que exponha a entrada do jogo como biblioteca nativa e passe contexto de Activity, caminhos privados e eventos de ciclo de vida. Manter o `main` desktop intacto. O wrapper chama a entrada de jogo compartilhada, em vez de duplicar a inicialização do engine.
- Criar um módulo Android Gradle para manifesto, Activity/NativeActivity, entry point nativo, biblioteca `.so`, seletor de documentos, ícones, permissões, empacotamento e assinatura. Se a Activity Kotlin/Java chamar diretamente APIs Rust, adicionar a ponte JNI necessária; manter renderização e gameplay em Rust.
- Adicionar uma feature `android` ou uma seleção de features por target. O build Android não deve ativar por padrão `gilrs`/controladores específicos de desktop, launcher, mods nativos dependentes de processo ou serviços que dependam de desktop. O áudio deve ser validado e habilitado separadamente com backend Android; `--no-default-features` é um caminho de compilação inicial, não a configuração final do produto.
- Separar o parser de argumentos e a descoberta de executável do contexto de inicialização. A preparação atual constrói um `Command` com `current_exe` e argumentos de processo; o host Android deve entregar uma estrutura de configuração explícita com raiz, recursos e opções.
- Iniciar a CI com verificação de compilação cruzada e empacotamento do wrapper arm64; depois acrescentar instalação e smoke test em aparelho/emulador. O emulador comprova inicialização, não desempenho de GPU móvel.
- Definir o nível mínimo do Android com base nos backends, NDK e aparelhos que serão suportados. Não prometer `armeabi-v7a` ou compatibilidade universal antes de validar dependências, memória e GPU.

### Multiplayer, permissões e reconexão

A base já implementa protocolo de cliente, conexão com servidores, jogo local/LAN e recursos de amigos/relay; não é necessário reescrever a pilha de gameplay em rede. O que falta é integração Android validada, autenticação móvel e política de ciclo de vida. A entrada direta por endereço deve preceder descoberta LAN e recursos de convite.

| Permissão/capacidade | Quando usar |
|---|---|
| `android.permission.INTERNET` | Declarar no manifesto para conexão direta a servidores e serviços online. Não pedir acesso amplo a arquivos para isso. |
| `android.permission.ACCESS_NETWORK_STATE` | Declarar somente se o app consultar mudanças/estado da rede para exibir conectividade e coordenar reconexão. |
| Acesso a Wi-Fi/multicast | Adicionar apenas quando a descoberta LAN realmente for implementada e depois de confirmar as permissões exigidas pelo mecanismo Android escolhido. Entrada manual por IP não deve solicitar permissões de descoberta. |
| Armazenamento | Não solicitar permissão ampla; usar SAF para seleção explícita de recursos e saves. |

- **Conectividade direta:** testar DNS, TLS, conexão e troca entre Wi-Fi e dados móveis. Fazer I/O de rede fora da thread de renderização, manter timeout e backoff de reconexão explícitos e não bloquear a UI esperando socket. Usar erros de rede compreensíveis e distinguir falha transitória de erro de conta/protocolo.
- **Autenticação:** a gestão de contas do launcher desktop não é o host Android. Implementar fluxo de autenticação no navegador do sistema com retorno por deep link/app link; guardar segredos persistentes no Android Keystore e tokens de sessão apenas pelo tempo necessário. Não colocar tokens em argumentos, logs ou intents não protegidas.
- **Latência e jitter:** manter leitura/escrita de sockets e processamento de pacotes fora da thread de renderização; mostrar estado/latência de forma útil sem bloquear controles. Validar conexão estável e cenários com RTT variável, jitter e perda; não alterar o tick do protocolo para esconder latência nem esperar rede dentro do handler de toque.
- **Reconexão:** observar perda e retorno da rede; evitar reconectar em loop agressivo. Em segundo plano, o MVP deve pausar renderização e escolher explicitamente entre desconectar de forma limpa ou permitir uma conexão de curta duração, sem manter CPU acordada. No retorno, apresentar estado e ação de reconectar; retomar a sessão sem reutilizar estado de socket inválido.
- **LAN:** começar com entrada manual por IP. Só ativar descoberta depois de validar broadcast/multicast em Wi-Fi Android e declarar as permissões específicas necessárias. Descoberta local não deve ser confundida com permissão de arquivos ou solicitada no primeiro arranque.
- **Amigos/relay e hosting:** integrar depois do acesso a servidor direto. Convites e relay adicionam login, deep links, rede em troca de conectividade e controles de background; hospedar mundo também aumenta consumo de CPU, bateria e memória.

## Fases de implementação

| Fase | Objetivo | Tarefas | Riscos principais | Critérios de aceite |
|---|---|---|---|---|
| 0 — Fechar o grafo Android | Compilar a parte Rust essencial para Android sem empacotar ainda o jogo completo. | Adicionar target `aarch64-linux-android`; configurar NDK/linker; identificar crates que não compilam; criar feature Android mínima; excluir dependências desktop do caminho Android; converter warnings/erros em lista de bloqueios. | Dependência nativa com suposições Linux desktop; recursos padrão ativando `gilrs`, áudio ou processos não suportados; macros/build scripts usando comandos do host. | `cargo check` e build do wrapper para o target arm64 concluem em CI; nenhuma dependência de biblioteca desktop é necessária no binário Android. |
| 1 — Host Android e superfície | Abrir o app, criar uma superfície e renderizar um frame com `wgpu`. | Criar wrapper nativo e Activity; fornecer diretório privado; integrar `winit`; ligar `resumed`/`suspended`; criar e recriar janela/superfície; registrar logs e erros de inicialização. | `main` convencional não ser um entry point de Activity; superfície inválida ao retomar; frame loop continuar ativo no background. | APK debug instala; mostra quadro de teste em Vulkan ou fallback GLES; ir para background e voltar várias vezes não trava nem deixa renderização contínua em background. |
| 2 — Dados e mundo local | Carregar recursos do usuário e operar saves com segurança. | Criar seletor de pacote preparado; validar versão/manifesto/hash; copiar para armazenamento privado; apontar `HORIZON_HOME`/configuração do jogo para raiz explícita; implementar importação e exportação de mundo por SAF. | Preparador depende de Java; pouco espaço; provider SAF lento ou sem seek; substituição/corrupção durante cópia. | Com pacote válido, o jogo chega ao menu e abre um mundo; saves persistem após encerramento; mundo exportado pode ser reimportado e continua legível; pacote inválido ou espaço insuficiente falha sem deixar instalação parcial ativa. |
| 3 — Touch, IME e UI | Jogar sem mouse nem teclado físico. | Integrar `WindowEvent::Touch` ao estado touch; ligar HUD ao frame; configurar joystick/mira/botões; adaptar telas e inventário; tratar IME, Voltar, cancelamentos e perda de foco; salvar configurações touch. | Dedo tocando controle também gerar clique de menu; coordenadas divergirem entre pixels, GUI scale e HUD; gestos se confundirem com mineração/arraste. | Em mundo de teste, movimento, mira, pulo, agachamento, ataque, uso, hotbar, inventário e pausa funcionam juntos; nenhum input fica preso depois de cancelar toque, perder foco ou girar a tela. |
| 4 — Compatibilidade gráfica e desempenho | Estabilizar FPS, memória, bateria e retomada em aparelhos de referência. | Negociar limites/capacidades; manter fallback Vulkan→GLES; criar perfil móvel; limitar distância, resolução, quadros e workers; desativar recursos incompatíveis; medir frame time CPU/GPU, memória, carregamento de chunks e comportamento térmico. | Diferenças entre drivers Adreno/Mali e versões do Android; OOM em textura/chunks; throttling que aparece só após uso contínuo; custo alto de compilação de shaders. | Cena de referência passa nos aparelhos definidos por tier; não há artefatos nem falhas em Vulkan/GLES; perfil baixo sustenta a meta de FPS escolhida sem crescimento persistente de memória ou throttling progressivo. |
| 5 — Periféricos e áudio | Tornar o app utilizável com controles físicos e som. | Conectar eventos de teclas/eixos Android a `android_runtime`; testar USB/Bluetooth, hotplug e troca automática entre touch/teclado/gamepad; validar backend Android do mixer; controlar volume e focagem ao suspender. | `gilrs` e suporte desktop não representarem eventos Android; variação de eixos e triggers; latência/compatibilidade do backend de áudio. | Gamepad conectado/desconectado não reinicia o jogo e não deixa botões pressionados; esquema de controles pode ser escolhido; áudio toca no dispositivo Android sem bloquear o loop de renderização. |
| 6 — Multiplayer direto | Entrar em servidor existente com rede e conta Android. | Implementar conta e retorno por deep link; permissão Internet; teste de TLS/DNS; entrada direta por endereço; observar rede; exibir desconexão e reconexão; garantir que nenhum token chegue aos logs. | Autenticação e sessão divergirem do launcher; rede móvel trocar IP; Doze/background; latência e timeout; certificados TLS. | Login e entrada em servidor direto funcionam em Wi-Fi e dados móveis; perda e retorno de rede dão erro recuperável; token expirado pede nova autenticação; alternar para background não deixa thread/socket inválido causar crash ao voltar. |
| 7 — LAN, distribuição e expansão | Completar o produto móvel sem acoplar features desktop. | Adicionar descoberta LAN com permissões mínimas; depois convites/relay e hosting se forem prioridades; definir política de mods; assinar e publicar AAB/APK; telemetria/crash report opt-in; matriz de aparelho/driver e regressão de saves. | Restrições de multicast; custos de hosting; incompatibilidade de mods nativos e runtime em subprocesso; fragmentação de aparelhos e políticas de loja. | Instalação e atualização preservam dados; permissões são pedidas somente no recurso que as usa; aparelhos da matriz passam testes de jogo, save, rede e retomada; falhas são diagnosticáveis sem coletar segredos. |

## Principais obstáculos técnicos e de desempenho

| Obstáculo | Impacto | Mitigação incremental |
|---|---|---|
| Preparação de recursos executa Java | Android não oferece o mesmo fluxo de instalação Java e subprocessos usado pelo preparador desktop. É o maior bloqueio para onboarding independente. | Primeiro importar pacote preparado pelo usuário; manter versão e integridade explícitas. Separar extração de dados de Java da importação para permitir portar somente o necessário depois. |
| Inicialização é centrada em processo desktop | `current_exe`, argumentos, ambiente, diretório corrente, subprocessos e launcher não equivalem a uma Activity. | Criar wrapper nativo fino e passar um `MobileLaunchConfig` explícito; manter a entrada desktop em paralelo. |
| Ciclo de vida e superfície | Android pode pausar, destruir e recriar superfície sem encerrar o processo; o estado de janela atual não implementa o ciclo móvel completo. | Suspender ticks/render não essenciais, descartar/recriar recursos de superfície, salvar estado recuperável e limpar inputs no ciclo de vida. |
| Integração touch incompleta | Os algoritmos touch e o desenho do HUD existem, mas não chegam ao jogo. Inventário e menus usam caminhos de mouse/teclado. | Conectar primeiro o toque ao jogo e depois adaptar telas; testar deduplicação de input e cenários multitouch. |
| Variação de GPU e drivers | Vulkan e GLES variam em formatos, limites, extensões, qualidade de driver e estabilidade de superfície. | Reusar `wgpu`, negociar capacidades, habilitar fallback real e validar em aparelhos físicos de mais de um fabricante/backend. |
| CPU e bateria | Worldgen, meshing, iluminação, tick do mundo e workers podem saturar CPU móvel; FPS alto eleva consumo e temperatura. O número de workers baseado em paralelismo disponível pode ser agressivo em aparelhos heterogêneos. | Perfil móvel com workers limitados, distância de mundo menor, renderização pausada em background, limite de FPS, dynamic resolution e testes térmicos prolongados. |
| Pressão de memória | Texturas, atlas, chunks residentes, buffers, caches e screenshots disputam RAM e memória de GPU compartilhada. O encerramento por OOM pode ser feito pelo sistema, sem erro Rust recuperável. | Limites por tier, cache/streaming controlados, distância baixa, evitar efeitos e capturas grandes por padrão, tratar OOM e registrar métricas sem dados pessoais. |
| Armazenamento SAF | Provedores podem ser lentos, remotos ou não oferecer semântica de arquivo/rename/seek esperada pelo jogo. | Usar SAF somente na borda de importação/exportação; trabalhar sobre cópia local privada durante o jogo. |
| Launcher, contas e mods | Launcher depende de UI desktop, chaveiro do SO e processos; mods nativos podem precisar de compilador ou subprocesso. | Não portar o launcher no MVP. Criar fluxo de conta Android próprio e definir suporte de mods por tipo; desativar nativos dependentes de processo até existir runtime suportado. |
| Áudio e controladores | O caminho desktop de controladores não é equivalente ao Android; backend de áudio precisa funcionar com as APIs nativas e políticas de foco. | Gatear features no Cargo; validar input Android dedicado e backend de áudio separadamente antes de incluí-los no build de lançamento. |
| Compatibilidade e diagnóstico | Emuladores não representam desempenho e estabilidade de drivers reais; ABI, versão Android e políticas de background variam. | Matriz pequena e explícita de aparelhos físicos por tier, testes repetíveis, logs de GPU/driver, cenários de suspensão e fluxo de relatório opt-in. |

## Ordem recomendada de entrega

1. Garantir que o grafo Rust reduzido compila para Android arm64.
2. Criar o host Android e provar janela, `wgpu` e ciclo de vida sem exigir assets.
3. Resolver importação de recursos e raiz privada de dados; abrir e salvar um mundo local.
4. Ligar controles touch ao gameplay e tornar inventário/telas utilizáveis.
5. Ajustar memória, distância, resolução, FPS, workers e retomada em hardware real.
6. Acrescentar áudio e controladores Android como caminhos independentes e testáveis.
7. Implementar autenticação e multiplayer direto; depois LAN, amigos/relay e distribuição ampliada.

Essa ordem preserva o engine compartilhado, limita alterações por target, mantém o cliente desktop funcionando e deixa os maiores riscos — entry point Android, preparação de assets, input touch e drivers de GPU — visíveis como gates testáveis.