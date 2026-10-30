# Local-path history sanitation — 2026-10-30

Owner authorized removing local machine paths from history before publication.
All 28 commits retain their original author/committer metadata, timestamps, messages
and source/build content. Only 18 Markdown blobs changed, generalizing home and
project paths. No engineering commits were squashed. No force-push occurred.

Earlier reports and metrics retain their original evidence commit IDs. Use this
mapping to find the corresponding sanitized commit; measurements were not changed.
Local app-managed checkpoint refs/reflogs can retain original objects; they are not
part of the `main` publication graph and must never be pushed with `--mirror`.
The pre-rewrite recovery bundle is outside the repository, not a publication asset.

| Original evidence commit | Sanitized commit |
| --- | --- |
| `54cf1df677efe6b9293aff508651afec4825de96` | `3fa74590a267803c0f519a40b91fe08c82e5188d` |
| `d30ad5fac075f4de7443038c82be141ccb2cfece` | `8b9778e844ed9cfe8d2fc5d31f44085a8ce63858` |
| `9719e71fe8902c0ebb802cd6333f85652b7952d5` | `904b2201a3ffbd7b1309fdf662b41c787b3a472e` |
| `2f375f3d5cc4bdbdb0f035fb0a097533fb369b43` | `64a7502b65b2669ececafadd78288fdc139f104b` |
| `2c4311264df285319562856b97d54a0066ad7f86` | `9a9556a045366cc48f34be858435a0bf9beb66fd` |
| `d672eca388b6080e6773493a912832313596baa1` | `a7d6f4993725a3a0a6cb634421800e70f05aab49` |
| `35eed8d212bef1209c8743ca40013758335db9fe` | `4764d16a971c0bb0ca3a5919bd62d4a16ce5c705` |
| `726d078d0bccfcef85d882c503abb9ecbcbb1fa5` | `93017f33c7f983d13f3734d347f41683dd85dded` |
| `a430c802377b0b9c9af96339b532a6b270acdb61` | `85cae22923879ec84df13b97e0075c3dfc23a93a` |
| `5556bd6236ecc1bf5aefc209e46fc9ab5536fb7b` | `8b880222bfd5a8af960aa56e7fe1a0bb809f481c` |
| `03357d688a465b11ef6c2b2a9d2417fd315074c9` | `a58338baedb2cb22d2a5122ea34c9b2e0b9d2990` |
| `e67c5bd8e3bb6fb6cd5e89d969e8855b2c9aeef0` | `15572d2b109648288fc471383657b60181b8f124` |
| `1c440708cef788e939a01bc300de0c0310734e05` | `858466eac588ee12b6f8343f221d2f0365a7064e` |
| `08aa5b76f4ee73b1a75dedeeb3d1f2e753fbb790` | `00d8b0dfc3fc47c828819ce1426ec23aacbb5635` |
| `083bbb7eb9b549d813654edbf7dae2ad11169964` | `4d7ff3d1ae004e6508eacacf1dc7d25e78bb5076` |
| `962452c32b01773e1733970624125bd79e619bce` | `50f202bf66c36f200784e2d494032614c6752877` |
| `09c5b3d72f3aa1e5b00ddeac3c317d7f7d7865da` | `e3b3784523df2e61e798792e593860e2e55e0a6f` |
| `80a145dcce3b904d5753f9132df64b4645f36941` | `f31f4df04db60eaf66897a86642c909b782ca204` |
| `187ecd7c76811c1dfe92d97e6af5cbc6e20b1a07` | `4d91903bc74ef7e327a811e0649db4c88df060ed` |
| `7eb80f83f95ca0ba9b7a1c80bf9b0d2a3bd00ac5` | `2c3ae1d6bf9b76b30210431d242866c6d54f47d0` |
| `c781a1fcb6fd5012010f0ed9722ea397a81a21e4` | `8cd5d71217d5f719fc9765966eacb6b45ca69425` |
| `f3c379dce850e13cc2f6a786a8551f375e092375` | `5dda9d05f367063d84ec364437155f2ab261672c` |
| `62720fc2aab055cfdc9cb60ed108a6f6cd726371` | `2d39654313edae97f317f6cca8da8837df518bd1` |
| `f5400a9f405b37cc2f3e6e223448ecc50ede126a` | `6fc28ae0828eb109ef20b71f6da2f44db0baf889` |
| `7cb350bdba7e25e19ccd0bfe4112d66e52ba4ba9` | `e752b4413c3dac56c06fa6c5e4c24604b239dd93` |
| `3ed0c0951f2decd08ac5d2e37fbfa62fa1489205` | `293c38eaf86b87ffb8d48d11cefb83d7d4108107` |
| `5add22dd20c0ee5cf6a60927a4d1db1e86a7dc5d` | `66f3df545124ff8bfe168cd25d0d88b09bff4ecf` |
| `9253d81ec69f9e24331795478ea7a66eccd1e335` | `97a02651e43e85958cf076186b7766a8f965530c` |
