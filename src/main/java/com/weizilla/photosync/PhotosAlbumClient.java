package com.weizilla.photosync;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Lists;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.collect.ImmutableList;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient.SearchMediaItemsPagedResponse;
import com.google.photos.types.proto.Album;
import com.google.photos.types.proto.MediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PhotosAlbumClient {
    private static final Logger logger = LoggerFactory.getLogger(PhotosAlbumClient.class);
    private static final List<String> REQUIRED_SCOPES =
        ImmutableList.of("https://www.googleapis.com/auth/photoslibrary.readonly");
    private static final String CREDENTIALS_FILE = "credentials.json";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private final String credentialsDir;
    private final String albumName;

    public PhotosAlbumClient(String albumName, String credentialsDir) {
        this.credentialsDir = credentialsDir;
        this.albumName = albumName;
    }

    public List<MediaItem> getItems() throws Exception {
        logger.info("Starting...");
        File credentialsFile = Paths.get(credentialsDir, CREDENTIALS_FILE).toFile();
        GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY,
            new InputStreamReader(new FileInputStream(credentialsFile)));
        String clientId = secrets.getDetails().getClientId();
        String clientSecret = secrets.getDetails().getClientSecret();

        GoogleAuthorizationCodeFlow flow = new Builder(
            new NetHttpTransport(),
            JSON_FACTORY,
            clientId,
            clientSecret,
            REQUIRED_SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new File(credentialsDir)))
            .setAccessType("offline")
            .build();

        LocalServerReceiver receiver = new LocalServerReceiver();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        UserCredentials credentials = UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRefreshToken(credential.getRefreshToken())
            .build();

        PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();

        logger.info("Init complete");

        try (PhotosLibraryClient client = PhotosLibraryClient.initialize(settings)) {

            logger.info("Getting albums...");
            Iterable<Album> albums = client.listAlbums().iterateAll();
            Album album = StreamSupport.stream(albums.spliterator(), false)
                .filter(a -> a.getTitle().equalsIgnoreCase(albumName))
                .findFirst()
                .orElseThrow();
            String id = album.getId();

            logger.info("Getting media items for album {}", albumName);
            SearchMediaItemsPagedResponse response = client.searchMediaItems(id);

            List<MediaItem> mediaItems = Lists.newArrayList(response.iterateAll());
            logger.info("Got {} media items for album {}", mediaItems.size(), albumName);

            Set<String> fileNames = mediaItems.stream()
                .map(MediaItem::getFilename)
                .collect(Collectors.toSet());

            if (mediaItems.size() != fileNames.size()) {
                throw new RuntimeException("Duplicate file names");
            }

            client.shutdown();

            return mediaItems;
        }
    }
}
