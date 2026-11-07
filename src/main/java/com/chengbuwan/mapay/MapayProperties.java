package com.chengbuwan.mapay;

import lombok.Data;

/**
 * Plugin setting values for the {@code mapay} form group (Setting
 * {@code mapay-setting}). Values are read at request time with
 * {@code ReactiveSettingFetcher.fetch("mapay", MapayProperties.class)}.
 *
 * <p>AppKey is a plain setting field, like AppID, as the site owner asked:
 * no Secret round-trip, just fill in both boxes and save.
 */
@Data
public class MapayProperties {

    /** AppID issued by the aggregated-login provider. */
    private String appId;

    /** AppKey issued by the aggregated-login provider. */
    private String appKey;

    /**
     * Aggregated-login service base URL, e.g. {@code https://login.mapay.cn}.
     * Blank means the built-in default. Users can change it in the plugin
     * settings if the provider moves; it is never hardcoded elsewhere.
     */
    private String apiBaseUrl;

    /**
     * Public site base URL used to build the callback URL, e.g.
     * {@code https://example.com} (no trailing slash). Blank means: use
     * Halo's configured external URL, falling back to the request URL.
     */
    private String siteUrl;
}
