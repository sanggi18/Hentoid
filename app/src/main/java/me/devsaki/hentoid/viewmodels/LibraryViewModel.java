package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagedList;

import com.annimon.stream.function.Consumer;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ZipUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static me.devsaki.hentoid.util.FileHelper.AUTHORIZED_CHARS;


public class LibraryViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO collectionDao = new ObjectBoxDAO(getApplication().getApplicationContext());
    // Library search manager
    private final ContentSearchManager searchManager = new ContentSearchManager(collectionDao);
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data
    private LiveData<PagedList<Content>> currentSource;
    private LiveData<Integer> totalContent = collectionDao.countAllBooks();
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();

    // Updated whenever a new search is performed
    private MutableLiveData<Boolean> newSearch = new MutableLiveData<>();


    public LibraryViewModel(@NonNull Application application) {
        super(application);
    }

    public void onSaveState(Bundle outState) {
        searchManager.saveToBundle(outState);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        searchManager.loadFromBundle(savedState);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.clear();
    }

    @NonNull
    public LiveData<PagedList<Content>> getLibraryPaged() {
        return libraryPaged;
    }

    @NonNull
    public LiveData<Integer> getTotalContent() {
        return totalContent;
    }

    @NonNull
    public LiveData<Boolean> getNewSearch() {
        return newSearch;
    }

    public Bundle getSearchManagerBundle() {
        Bundle bundle = new Bundle();
        searchManager.saveToBundle(bundle);
        return bundle;
    }

    // =========================
    // ========= LIBRARY ACTIONS
    // =========================

    /**
     * Perform a new library search
     */
    private void performSearch() {
        if (currentSource != null) libraryPaged.removeSource(currentSource);

        searchManager.setContentSortOrder(Preferences.getContentSortOrder());
        currentSource = searchManager.getLibrary();

        libraryPaged.addSource(currentSource, libraryPaged::setValue);
    }

    /**
     * Perform a new universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    public void searchUniversal(@NonNull String query) {
        searchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        searchManager.setQuery(query);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Perform a new search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param metadata Metadata to use for the search
     */
    public void search(@NonNull String query, @NonNull List<Attribute> metadata) {
        searchManager.setQuery(query);
        searchManager.setTags(metadata);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Toggle the favourite filter
     */
    public void toggleFavouriteFilter() {
        searchManager.setFilterFavourites(!searchManager.isFilterFavourites());
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Set the mode (endless or paged)
     */
    public void setPagingMethod(boolean isEndless) {
        searchManager.setLoadAll(!isEndless);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Update the order of the list
     */
    public void updateOrder() {
        newSearch.setValue(true);
        performSearch();
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param content Content whose favourite state to toggle
     */
    public void toggleContentFavourite(@NonNull final Content content) {
        if (content.isBeingDeleted()) return;

        // Flag the content as "being favourited" (triggers blink animation)
        content.setIsBeingFavourited(true);
        collectionDao.insertContent(content);

        compositeDisposable.add(
                Single.fromCallable(() -> doToggleContentFavourite(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                },
                                Timber::e
                        )
        );
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param contentId ID of the content whose favourite state to toggle
     * @return Resulting content
     */
    @WorkerThread
    private Content doToggleContentFavourite(long contentId) {

        // Check if given content still exists in DB
        Content theContent = collectionDao.selectContent(contentId);

        if (theContent != null) {
            theContent.setFavourite(!theContent.isFavourite());
            theContent.setIsBeingFavourited(false);

            // Persist in it JSON
            if (!theContent.getJsonUri().isEmpty())
                ContentHelper.updateJson(getApplication(), theContent);
            else ContentHelper.createJson(getApplication(), theContent);

            // Persist in it DB
            collectionDao.insertContent(theContent);

            return theContent;
        }

        throw new InvalidParameterException("ContentId " + contentId + " does not refer to a valid content");
    }

    /**
     * Add the given content to the download queue
     *
     * @param content Content to be added to the download queue
     */
    public void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus) {
        collectionDao.addContentToQueue(content, targetImageStatus);
    }

    /**
     * Set the "being deleted" flag of the given content
     *
     * @param content Content whose flag to set
     * @param flag    Value of the flag to be set
     */
    public void flagContentDelete(@NonNull final Content content, boolean flag) {
        content.setIsBeingDeleted(flag);
        collectionDao.insertContent(content);
    }

    /**
     * Delete the given list of content
     *
     * @param contents   List of content to be deleted
     * @param onComplete Callback to run when the whole operation succeeds
     * @param onError    Callback to run when an error occurs
     */
    public void deleteItems(@NonNull final List<Content> contents, Runnable onComplete, Consumer<Throwable> onError) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contents) flagContentDelete(c, true);

        compositeDisposable.add(
                Observable.fromIterable(contents)
                        .subscribeOn(Schedulers.io())
                        .flatMap(s -> Observable.fromCallable(() -> doDeleteContent(s)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                },
                                onError::accept,
                                onComplete::run
                        )
        );
    }

    /**
     * Delete the given content
     *
     * @param content Content to be deleted
     * @return Content that has been deleted
     * @throws ContentNotRemovedException When any issue occurs during removal
     */
    @WorkerThread
    private Content doDeleteContent(@NonNull final Content content) throws ContentNotRemovedException {
        try {
            // Check if given content still exists in DB
            Content theContent = collectionDao.selectContent(content.getId());

            if (theContent != null) {
                ContentHelper.removeContent(getApplication(), theContent, collectionDao);
                Timber.d("Removed item: %s from db and file system.", theContent.getTitle());
                return theContent;
            }
            throw new ContentNotRemovedException(content, "ContentId " + content.getId() + " does not refer to a valid content");
        } catch (Exception e) {
            Timber.e(e, "Error when trying to delete %s", content.getId());
            throw new ContentNotRemovedException(content, "Error when trying to delete " + content.getId() + " : " + e.getMessage(), e);
        }
    }

    /**
     * Archive the given Content into a temp ZIP file
     *
     * @param content   Content to be archived
     * @param onSuccess Callback to run when the operation succeeds
     */
    public void archiveContent(@NonNull final Content content, Consumer<File> onSuccess) {
        Timber.d("Building file list for: %s", content.getTitle());

        DocumentFile bookFolder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(content.getStorageUri()));
        if (null == bookFolder || !bookFolder.exists()) return;

        List<DocumentFile> files = FileHelper.listDocumentFiles(getApplication(), bookFolder, null); // Everything (incl. JSON and thumb) gets into the archive
        if (!files.isEmpty()) {
            // Create folder to share from
            File sharedDir = new File(getApplication().getExternalCacheDir() + "/shared");
            if (FileHelper.createDirectory(sharedDir)) {
                Timber.d("Shared folder created.");
            }

            // Clean directory (in case of previous job)
            if (FileHelper.cleanDirectory(sharedDir)) {
                Timber.d("Shared folder cleaned up.");
            }

            // Build destination file
            File dest = new File(getApplication().getExternalCacheDir() + "/shared",
                    content.getTitle().replaceAll(AUTHORIZED_CHARS, "_") + ".zip");
            Timber.d("Destination file: %s", dest);

            compositeDisposable.add(
                    Single.fromCallable(() -> ZipUtil.zipFiles(files, dest))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(onSuccess::accept,
                                    Timber::e)
            );
        }
    }
}
