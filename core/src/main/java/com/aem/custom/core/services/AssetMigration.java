package com.aem.custom.core.services;

import com.aem.custom.core.pojos.OneDriveFile;
import com.google.gson.JsonObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public interface AssetMigration {

    /* HDD Migration Methods */
    void migrateAssetsFromHDD(int startIndex, int endIndex, String sourcePath, String destinationPath, JsonObject responseObject);


    /* OneDrive Migration Methods */

    String getGraphAPIAccessToken();

    String getListChildrenAPIResponse(String accessToken, String sourcePath) throws IOException;

    List<OneDriveFile> extractOneDriveFilesFromResponse(String listChildrenAPIResponse) throws JSONException;

    void migrateAssetsFromOneDrive(int startIndex, int endIndex, String destinationPath, List<OneDriveFile> oneDriveFilesList, JsonObject responseJSON);

}
