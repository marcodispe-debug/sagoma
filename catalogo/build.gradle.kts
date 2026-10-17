plugins {
    alias(libs.plugins.android.asset.pack)
}

// Asset pack di Google Play con il catalogo (arredi, materiali, luci): src/main/assets, stessi percorsi di prima
// (furniture/, materials/, env/). "install-time": arriva insieme all'app e l'AssetManager lo vede come gli altri asset.
assetPack {
    packName.set("catalogo")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
