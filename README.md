# Meu Treino

Aplicativo Android offline em Kotlin, Jetpack Compose e Room. Funciona no Android 14 e começa com a lista de rotinas vazia.

- **Criar rotina** salva um modelo com exercícios de força e cardio.
- **Iniciar** abre uma sessão a partir da rotina. **Registrar treino avulso** abre uma sessão em branco.
- Na força, informe carga e repetições. A carga aceita ponto ou vírgula. Para cardio, informe minutos e, se quiser, distância em quilômetros.
- Em **Histórico**, abra **Editar registro** para corrigir uma sessão já salva.
- O último registro do exercício aparece durante a sessão. A consulta considera a mesma rotina e unidade. Treinos avulsos consultam registros avulsos.
- Se você configurar uma meta de repetições, o aviso aparece quando todas as séries registradas atingem a meta com a mesma carga e a quantidade de séries planejadas foi alcançada. Essa regra pertence à versão genérica.

## Prévia da interface

Imagem ilustrativa com dados de exemplo, baseada na interface do projeto.

![Prévia das telas Rotinas e Registrar treino](docs/previa-interface.jpg)

## Gerar o APK

Use JDK 17, Android SDK Platform 35 e Gradle 8.9. Configure ANDROID_HOME ou abra o projeto no Android Studio para configurar o SDK. Para gerar os arquivos oficiais do Wrapper a partir de uma instalação do Gradle 8.9 e compilar:

```sh
gradle wrapper --gradle-version 8.9 --distribution-type bin
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk meu-treino.apk
```

Copie o APK para o celular, abra-o pelo app Arquivos e permita **Instalar apps desconhecidos** para esse app caso o Android solicite. O ícone aparecerá como **Meu Treino**.

## Validar

Com um emulador ou aparelho conectado com depuração USB:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Teste manual no Android 14: crie duas rotinas, registre carga com vírgula em uma, mude para a outra e salve outro treino. Feche e reabra o app e confirme ambos os registros no histórico. Inicie a primeira rotina novamente e confira o último treino. Repita com e sem atingir a meta. Valide também a digitação com o teclado aberto e o funcionamento em modo avião.
