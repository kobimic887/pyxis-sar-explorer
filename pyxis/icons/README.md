The wordmark paths come from Pyxis Discovery's client/src/components/PyxisLogo.jsx
(read-only product reference). The app icon isolates its citron arrow mark, rather
than the unrelated template favicon. pyxis-logo.svg retains the complete wordmark.
RenderBrand.java uses the bundled Batik dependency to rasterize these SVGs; PNGs
were resized with macOS sips, ICNS with iconutil, and ICO contains PNG entries.
