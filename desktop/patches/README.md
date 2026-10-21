# Parches propios de Kaze

Archivos `.patch` (formato `git diff`) que se aplican sobre el código de Zen después de `sync_zen.py`.
Se aplican en orden alfabético: usa prefijos como `001-`, `002-`.

Para crear uno: edita archivos dentro de `desktop/build/zen/`, y luego
`git -C desktop/build/zen diff > desktop/patches/001-mi-cambio.patch`.
