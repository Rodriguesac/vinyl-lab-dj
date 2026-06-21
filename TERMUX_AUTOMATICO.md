# Publicar e baixar APK pelo Termux

Este projeto vem com um comando que faz quase todo o processo sozinho:

1. cria o repositório GitHub, se ele ainda não existir;
2. cria/atualiza a branch `main`;
3. envia os arquivos;
4. espera o GitHub Actions compilar;
5. baixa o APK pronto para a pasta `APK_GERADO` dentro do projeto.

## Preparação única no Termux

Instale o GitHub CLI (`gh`) uma única vez. Ter apenas `git` conectado não basta para **criar** um repositório automaticamente no GitHub.

```bash
pkg update -y
pkg install -y git gh unzip

gh auth login -h github.com -p https -w
```

No login, abra o navegador, entre na sua conta GitHub e autorize. Depois confirme:

```bash
gh auth status
```

## Publicar este projeto pela primeira vez

Depois de extrair o ZIP, abra a pasta do projeto e rode:

```bash
cd ~/VinylLab-DJ-Android
bash publicar_no_github_e_baixar_apk.sh vinyl-lab-dj private
```

O primeiro nome (`vinyl-lab-dj`) será o nome do repositório. Você pode trocar, por exemplo:

```bash
bash publicar_no_github_e_baixar_apk.sh meu-app-de-disco public
```

O segundo parâmetro é `private` ou `public`. Use `private` se não quiser que outras pessoas vejam o código.

Ao terminar, o APK estará em:

```bash
~/VinylLab-DJ-Android/APK_GERADO/
```

## Próximas atualizações

Após editar arquivos, use exatamente o mesmo comando. Ele detecta que o repositório já existe, faz o commit, envia as alterações, acompanha a nova compilação e substitui o APK da pasta `APK_GERADO`.

```bash
bash publicar_no_github_e_baixar_apk.sh vinyl-lab-dj private
```

## Notas importantes

- A primeira execução pode pedir autorização do GitHub CLI no navegador.
- O APK é compilado pelos servidores do GitHub, então o Termux não precisa baixar Android Studio nem SDK Android completo.
- Se a compilação falhar, o comando mostra o log das etapas com erro e mantém o link da aba **Actions** no final.
