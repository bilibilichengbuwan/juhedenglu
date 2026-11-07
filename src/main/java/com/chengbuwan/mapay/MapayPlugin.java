package com.chengbuwan.mapay;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Aggregated social login plugin (WeChat / QQ / Alipay via Mapay).
 *
 * <p>This plugin does not define any custom extension models, so there is no
 * scheme to register. All behaviour lives in {@link MapayAuthWebFilter} plus
 * the AuthProvider / Setting / ReverseProxy resources under
 * {@code src/main/resources/extensions}.
 *
 * <p>Derived from halo-sigs/plugin-oauth2 (GPL-3.0); the OAuth2 client
 * registrations and provider-specific code were removed and replaced with the
 * Mapay aggregated-login flow.
 */
@Component
public class MapayPlugin extends BasePlugin {

    public MapayPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
