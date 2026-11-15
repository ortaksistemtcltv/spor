rootProject.name = "CloudStreamSporB"
include("SporBProvider")

// Site adaptörlerini üretim dizininden önce denemek için: -PpreviewCatalogs=true
if (providers.gradleProperty("previewCatalogs").orNull == "true") {
    include("FilmProvider", "DiziProvider")
}
