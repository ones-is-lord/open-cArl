# Drop the source art here

```
art/splash.png    the detailed CRT artwork  -> launch screen
art/icon.png      the round badge           -> launcher icon + title-bar avatar
art/logo.png      the brush wordmark        -> title bar and drawer
```

Then:

```bash
tools/import-art.sh art/splash.png art/icon.png art/logo.png
```

Attaching an image to a chat message does not put a file on disk, so the build
cannot read it. These three files have to exist here (or anywhere on the
filesystem) before the importer can run.
