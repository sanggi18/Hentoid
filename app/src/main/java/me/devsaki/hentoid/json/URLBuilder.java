package me.devsaki.hentoid.json;

/**
 * General builder for URLs
 *
 * @deprecated Replaced by {@link me.devsaki.hentoid.services.ImportService} methods; class is kept for retrocompatibilty
 */
@Deprecated
public class URLBuilder {

    private String url;
    private String description;

    public String getId() {
        int idxStart = url.lastIndexOf('/');
        String id = url.substring(idxStart);
        String category = url.replace(id, "");
        category = category.substring(category.lastIndexOf('/'));

        return category + id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
