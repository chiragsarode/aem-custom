package com.aem.custom.core.services.impl;

import com.aem.custom.core.pojos.OneDriveFile;
import com.aem.custom.core.services.AssetMigration;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Component(service = AssetMigration.class)
public class AssetMigrationImpl implements AssetMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMigrationImpl.class);

    @Reference
    AssetMigrationConfigurationImpl config;

    private static final String CT_JSON = "application/json";

    private static final String BEARER = "Bearer ";

    private static final String DOCUMENTS_FOLDER_ENDPOINT = "https://graph.microsoft.com/v1.0/users/b777bc4c50d283ac/drive/items/root:/documents:/children?$select=id,name,size,content.downloadUrl,file";
    /*
    Selecting a particular drive by ID -> "https://graph.microsoft.com/v1.0/me/drives/b!-RIj2DuyvEyV1T4NlOaMHk8XkS_I8MdFlUCq1BlcjgmhRfAj3-Z8RY2VpuvV_tpd/root/children?$select=name,file,content.downloadUrl";
    Use if user id available -> "https://graph.microsoft.com/v1.0/users/b777bc4c50d283ac/drive/items/root:/documents:/children?$select=id,name,size,content.downloadUrl,file"
    Alternative : /me (may not work in AAD) -> "https://graph.microsoft.com/v1.0/me/drive/items/root:/documents:/children?$select=id,name,size,content.downloadUrl,file"
     */
    public void migrateAssetsFromHDD(int startIndex, int endIndex, String sourcePath, String destinationPath, JsonObject responseObject) {
        LOGGER.debug("migrateAssetsFromHDD() starts");

        //Converting these to file system
        File folder = new File(sourcePath);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            //Looping for each files
            for (int index = startIndex; index <= Math.min(endIndex, listOfFiles.length - 1); index++) {
                File file = listOfFiles[index];
                try {
                    if (file.isFile()) {
                        ProcessBuilder processBuilder;
                        String modifiedFileName = file.getName().replaceAll("\\s", "_");
                        if (config.isThisAEMaaCS()) {
                            processBuilder = new ProcessBuilder("curl", "-H", "Authorization: Bearer "
                                    + config.getBearerToken(), "-T", file.getPath(), config.getAemURL()
                                    + destinationPath + "/" + modifiedFileName);
                        } else {
                            processBuilder = new ProcessBuilder("curl", "-u", config.getUserId() + ":"
                                    + config.getUserPassword(), "-T", file.getPath(), config.getAemURL()
                                    + destinationPath + "/" + modifiedFileName);
                        }
                        Process uploadProcess = processBuilder.start();
                        uploadProcess.waitFor();
                        int uploadExitCode = uploadProcess.exitValue();
                        uploadProcess.destroy();
                        responseObject.addProperty(index + " - " + file.getPath(), uploadExitCode);
                        LOGGER.debug("Index - {} | File Path : {} | Modified FileName : {} |  Exit Code : {} ", index, file.getPath(), modifiedFileName, uploadExitCode);
                    } else if (file.isDirectory()) {
                        responseObject.addProperty(index + " - " + file.getPath(), "DIRECTORY");
                        LOGGER.debug("Index - {} | File Path : {} | Exit Code : DIRECTORY ", index, file.getPath());
                    }
                } catch (InterruptedException e) {
                    LOGGER.debug("ERROR : AssetMigrationImpl: Index - {} | File Path : {} | Exit Code : InterruptedException ", index, file.getPath());
                    responseObject.addProperty(index + " - " + file.getPath(), "InterruptedException");
                    LOGGER.error("InterruptedException in process upload :", e);
                } catch (IOException e) {
                    LOGGER.debug("ERROR : AssetMigrationImpl: Index - {} | File Path : {} | Exit Code : IOException", index, file.getPath());
                    responseObject.addProperty(index + " - " + file.getPath(), "IOException");
                    LOGGER.error("IOException in process upload :", e);
                } catch (Exception e) {
                    LOGGER.debug("ERROR : AssetMigrationImpl: Index - {} | File Path : {} | Exit Code : {} ", index, file.getPath(), e.getMessage());
                    responseObject.addProperty(index + " - " + file.getPath(), e.getMessage());
                    LOGGER.error("Exception in process upload :", e);
                }
            }
        }
        LOGGER.debug("AssetMigrationImpl : migrateAssetsFromHDD() ends");
    }


    public void migrateAssetsFromOneDrive(int startIndex, int endIndex, String destinationPath, List<OneDriveFile> oneDriveFilesList, JsonObject responseJSON) {
        for (int index = startIndex; index <= Math.min(endIndex, oneDriveFilesList.size() - 1); index++) {
            OneDriveFile oneDriveFile = oneDriveFilesList.get(index);
            if (oneDriveFile.isFolder()) {
                responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "FOLDER");
                LOGGER.debug("Index {} - {} is a Folder", index, oneDriveFile.getName()); //handle the logging / reporting
            } else {
                Optional<byte[]> oneDriveFileBinaryData = downloadAssetBinaryFromOneDrive(index, oneDriveFile, responseJSON);
                if (oneDriveFileBinaryData.isPresent()) {
                    uploadAssetBinaryToAEM(index, oneDriveFile, oneDriveFileBinaryData.get(), destinationPath, responseJSON);
                } else {
                    LOGGER.error("ERROR - Failed to download the file.");
                }
            }
        }
    }

    public String getListChildrenAPIResponse(String accessToken, String sourcePath) throws IOException {
        String listChildrenEndpoint = DOCUMENTS_FOLDER_ENDPOINT; //replace placeholder with sourcePath here
        if (StringUtils.isNotBlank(accessToken)) {
            LOGGER.debug("GET listChildren Endpoint: {} {} ", listChildrenEndpoint, sourcePath);

            final HttpGet httpGet = new HttpGet(listChildrenEndpoint);
            httpGet.setHeader(HttpHeaders.ACCEPT, CT_JSON);
            httpGet.setHeader(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
            try (CloseableHttpClient clientPost = HttpClients.createDefault();
                 CloseableHttpResponse responseGet = clientPost.execute(httpGet)) {
                String apiResponseString = EntityUtils.toString(responseGet.getEntity());
                LOGGER.debug("GET listChildren API Status Code: {} ", responseGet.getStatusLine().getStatusCode());
                LOGGER.debug("GET listChildren API Response : {} ", apiResponseString);
                if (responseGet.getStatusLine().getStatusCode() == 200) {
                    return apiResponseString;
                } else {
                    LOGGER.error("ERROR - GET listChildren - Status code is not 200, Please check the API response!");
                }
            }
        } else {
            LOGGER.error("GET listChildren - AccessToken is Empty or Null");
        }
        return StringUtils.EMPTY;
    }

    public List<OneDriveFile> extractOneDriveFilesFromResponse(String listChildrenAPIResponse) throws JSONException {
        List<OneDriveFile> oneDriveFilesList = new ArrayList<>();
        JSONObject listChildrenAPIResponseJSON = new JSONObject(listChildrenAPIResponse);
        JSONArray listOfChildren = listChildrenAPIResponseJSON.has("value") ? listChildrenAPIResponseJSON.getJSONArray("value") : null;
        if (nonNull(listOfChildren) && listOfChildren.length() > 0) {
            for (int i = 0; i < listOfChildren.length(); i++) {
                JSONObject fileDetailsJSON = listOfChildren.getJSONObject(i);
                OneDriveFile oneDriveFile = new OneDriveFile();
                oneDriveFile.setName(fileDetailsJSON.getString("name"));
                if (fileDetailsJSON.has("file")) {
                    oneDriveFile.setMimeType(fileDetailsJSON.getJSONObject("file").getString("mimeType"));
                    oneDriveFile.setDownloadURL(fileDetailsJSON.getString("@microsoft.graph.downloadUrl"));
                    oneDriveFile.setFolder(false);
                } else {
                    oneDriveFile.setFolder(true);
                }
                oneDriveFilesList.add(oneDriveFile);
            }
        } else {
            LOGGER.error("ERROR - GET listChildren - JSONArray listOfChildren is Null / Empty, Please check the API response!");
        }
        return oneDriveFilesList;
    }

    private Optional<byte[]> downloadAssetBinaryFromOneDrive(int index, OneDriveFile oneDriveFile, JsonObject responseJSON){
        LOGGER.debug("downloadAssetBinaryFromOneDrive() Download URL: {} ", oneDriveFile.getDownloadURL());
        final HttpGet httpGet = new HttpGet(oneDriveFile.getDownloadURL());
        try (CloseableHttpClient clientGet = HttpClients.createDefault();
             CloseableHttpResponse responseGet = clientGet.execute(httpGet)) {
            LOGGER.debug("downloadAssetBinaryFromOneDrive() API Status Code: {} ", responseGet.getStatusLine().getStatusCode());
            if (responseGet.getStatusLine().getStatusCode() == 200) {
                LOGGER.debug("Index - {} : FileName - {} - SUCCESSFULLY DOWNLOADED", index, oneDriveFile.getName());
                return Optional.of(EntityUtils.toByteArray(responseGet.getEntity()));
            } else {
                responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "Download Failed");
                LOGGER.error("GET downloadFile - Index - {} : FileName - {} Response Entity if status code is not 200", index, oneDriveFile.getName());
            }
        } catch (IOException e) {
            responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "IOException (Download)");
            LOGGER.error("GET downloadFile - IOException occurred while trying download the File", e);
        }
        return Optional.empty();
    }

    private void uploadAssetBinaryToAEM(int index, OneDriveFile oneDriveFile, byte[] oneDriveFileBinaryData, String destinationPath, JsonObject responseJSON) {
        destinationPath = destinationPath.replace("/content/dam", "/api/assets");
        String uploadEndpoint = config.getAemURL() + destinationPath + "/" + URLEncoder.encode(oneDriveFile.getName(), StandardCharsets.UTF_8);
        HttpPost httpPost = new HttpPost(uploadEndpoint);
        String credToEncode = config.getUserId() + ":" + config.getUserPassword();
        String encryptedToken = Base64.getEncoder().encodeToString(credToEncode.getBytes());
        httpPost.setHeader("Authorization", "Basic " + encryptedToken);
        httpPost.setHeader("Content-Type", oneDriveFile.getMimeType());

        HttpEntity entity = new ByteArrayEntity(oneDriveFileBinaryData);
        httpPost.setEntity(entity);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse responsePost = client.execute(httpPost)) {
            int statusCode = responsePost.getStatusLine().getStatusCode();
            LOGGER.debug("uploadAssetBinaryToAEM() API Status Code: {} ", statusCode);
            if (responsePost.getStatusLine().getStatusCode() == 201) {
                responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "SUCCESS");
                LOGGER.debug("Index - {} : FileName - {} - SUCCESSFULLY UPLOADED", index, oneDriveFile.getName());
            } else if (responsePost.getStatusLine().getStatusCode() == 409) {
                responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "CONFLICT - File Already Exists");
                LOGGER.error("CONFLICT - File Already Exists at given path!!");
            } else {
                responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "ERROR - " + statusCode);
                LOGGER.error("ERROR - AEM Asset upload API response {}", statusCode);
            }
        } catch (IOException e) {
            responseJSON.addProperty(index + " - " + oneDriveFile.getName(), "IOException (Upload)");
            LOGGER.error("ERROR uploadAssetBinaryToAEM() - IOException occurred while trying to execute Asset POST API", e);
        }
    }

    public String getGraphAPIAccessToken() {
        return "EwCYA8l6BAAUbDba3x2OMJElkF7gJ4z/VbCPEz0AAc7Q0LgABf5H9cCrQm7E5Zp4L/yUsokFVePOd20QYH82jqBDNxmjexuXS/yshIkOJdLQYQpeYgeH8xO6Eup3PobRdPA3g3qUBkMNv5sRx7E4jDmqI3QX3PcTt92BbJSgEgaLsXpD3DyewC0YfXSmBPGIvI7yEsb8D5XDP16Yk9dqrzxnn+CxR36u4JqucyyChCqKn8WTniOHra2xtxj+QLQeR6TO/HklHBJsskplCxNfQ1RHFMyFndWOgxXFc6/3N1Jx8fHlF3iq00NoeIU5yl+DJMDh8T9/Zi7750fvuUv8u4p+1hdhpkbfzwzqYlwgqzAaacqEqTt/wIzwx3jmPjEQZgAAEMrm7lHvKOwGoLrlgiviAgpgAk9HfeedrCj0soCZxGcSeRVLOBOMiMpwe/igJvm5asqnAfbkx0px5MLrsCHtq7dDRrTnP44zL6O5L839O+JZoCzDzHScFFHAVDL9LF0+b8R1Gc6Kw13iVwZsYBlFhlIDc+yS4AIUn2qbgYPA8qFVevdLKISYpOOsFmvQ1HRyMnmSn4+26IAIu6SG7OxQNbLbg2olZwf5LxLckoIEgcvRzcjzTpKoQpBqf/OycYSv1aDt41Zpv8ypXAD0nOF6ni1bHe8OKOGvo99KB4Gl0Mmav/09KQlDV2b08Vxq/yb2aSYjYimUTuRI1+uUxoFwJ93XqThkGK9hmg1iIvy1ME4GRXi8dMtKOp8EzQnN3v4AM2md/U9s6AryWTFymMGrN3fwOec1zf4kJ8NRZaoFUeAoJ01NQlOlQTEuf/xfMX3DBp3AIvCQbqF3xtGlB0rnW0P+uljT5pAsoK24At3a86nIvXeY+C3q+YfzzVFNlihdLdYg8bZzGbFzNdLFUxMbM4EDxenD5VPqxCT8SX2kCDRqFVlys6jS3iUxvzeRVqI+gB7R/hSnqbfTCwiqoWGHnbaE/j8wC2rvuTOdTHy9n7KTPW6n6ceXBPNp5MTZcT8txg//LOOgS4haV3aMLFzJ/UgIhCag26pMpFkiTZMBXv0Hh9r4HyXJLlWkhITsYGbqPLAPWI7tLigrbFubEJdvjjHiNsYcJYM6Cef2MjCxrtS7qPLJg/gOy/0tmQcMO2fN8gvBWdD5VN2gLL2iO6cRhrQKekRr+qM5fNQ+yj+S5bUvJymvV9SszHo3/2c2q76dQjEbjAI=";
    }
}
