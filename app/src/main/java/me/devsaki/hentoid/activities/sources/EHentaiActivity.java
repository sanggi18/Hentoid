package me.devsaki.hentoid.activities.sources;

import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.sources.EHentaiServer;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Implements E-Hentai source
 */
public class EHentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "e-hentai.org";
    private static final String[] GALLERY_FILTER = {"e-hentai.org/g/[0-9]+/[A-Za-z0-9\\-_]+"};

    Site getStartSite() {
        return Site.EHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new EHentaiWebClient(GALLERY_FILTER, this);
        CookieManager.getInstance().setCookie(Site.EHENTAI.getUrl(), "sl=dm_2");
        client.restrictTo(DOMAIN_FILTER);
        // E-h serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        return client;
    }

    private class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(String[] filter, WebContentListener listener) {
            super(filter, listener);
        }

        // We keep calling the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            String[] galleryUrlParts = urlStr.split("/");
            EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);
            compositeDisposable.add(EHentaiServer.API.getGalleryMetadata(query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata ->
                            {
                                isHtmlLoaded = true;
                                listener.onResultReady(metadata.toContent(urlStr), quickDownload);
                            },
                            throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                isHtmlLoaded = true;
                                listener.onResultFailed();
                            })
            );
            return null;
        }
    }
}
