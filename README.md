# Vinyl Lab DJ — Android v1.0.0

Projeto Android nativo em Kotlin + Jetpack Compose. Ele já possui um workflow do GitHub Actions para gerar o APK de teste automaticamente após o envio dos arquivos para a branch `main`.

## O que já está funcionando

- Escolher uma música local usando o seletor seguro do Android.
- Play, pausa, avançar e voltar cinco segundos.
- Disco desenhado em tela, com rotação durante a reprodução.
- Scratch/jog manual: arrastar o disco move o ponto da música.
- Velocidade de 0,50x até 2,00x.
- Pitch/tom independente de 0,50x até 2,00x.
- Loop de oito segundos a partir do ponto atual.
- Jog reverso: move a faixa para trás com o disco girando no sentido contrário.

## Limite técnico desta versão

O botão **Jog reverso** não toca o áudio invertido de forma contínua. Ele é uma navegação reversa em passos curtos. Para ter som invertido real, scratch sem cortes e gravação da mixagem, a próxima fase deve usar um motor de áudio próprio com buffers PCM/AAudio ou biblioteca de áudio especializada.

## Como gerar pelo GitHub

1. Crie um repositório vazio no GitHub.
2. Extraia este ZIP e envie **o conteúdo da pasta**, mantendo `app`, `.github`, `build.gradle.kts` e `settings.gradle.kts na raiz.
3. Faça o push para a branch `main`.
4. Abra a aba **Actions** no GitHub e entre na execução **Gerar APK Debug**.
5. Ao finalizar, abra **Artifacts** e baixe `VinylLab-DJ-debug-apk`.

O APK estará em `app/build/outputs/apk/debug/app-debug.apk` dentro do artefato.

## Para alterar nome e versão

Arquivo: `app/build.gradle.kts`

```kotlin
applicationId = "com.rodriguesacai.vinyllab"
versionCode = 1
versionName = "1.0.0"
```

## Tecnologias

- Android Kotlin
- Jetpack Compose
- Media3 / ExoPlayer
- GitHub Actions

## Automação pelo Termux

Há um comando pronto chamado `publicar_no_github_e_baixar_apk.sh`. Depois de autenticar uma vez com `gh auth login`, ele cria ou atualiza o repositório, faz o push, acompanha o GitHub Actions e baixa o APK final em `APK_GERADO`.

Leia **TERMUX_AUTOMATICO.md** e execute:

```bash
bash publicar_no_github_e_baixar_apk.sh vinyl-lab-dj private
```
