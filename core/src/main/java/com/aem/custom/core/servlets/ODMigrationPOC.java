package com.aem.custom.core.servlets;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.nonNull;

import org.apache.http.HttpEntity;

@Component(service = {Servlet.class})
@SlingServletPaths(value = "/bin/od-migration")
@ServiceDescription("MS Graph API Servlet")
public class ODMigrationPOC extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ODMigrationPOC.class);

    private static final String CT_JSON = "application/json";

    private static final String BEARER = "Bearer ";

    private static final String DOCUMENTS_FOLDER_ENDPOINT = "https://graph.microsoft.com/v1.0/me/drives/b!-RIj2DuyvEyV1T4NlOaMHk8XkS_I8MdFlUCq1BlcjgmhRfAj3-Z8RY2VpuvV_tpd/root/children?$select=name,file,content.downloadUrl";
    //"https://graph.microsoft.com/v1.0/users/b777bc4c50d283ac/drive/items/root:/documents:/children?$select=id,name,size,content.downloadUrl,file";
    //Alternative is /me (may not work in AAD) -> "https://;graph.microsoft.com/v1.0/me/drive/items/root:/documents/folderName:/children?$select=id,name,size,content.downloadUrl,file";

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        try {
            String accessToken = "";
            List<OneDriveFile> oneDriveFilesList = getOneDriveFilesList(accessToken);
            oneDriveFilesList.forEach(this::transferAssetToDAM);
        } catch (IOException | JSONException e) {
            LOG.error("GET listChildren - Exception occurred while trying to execute Favorites API", e);
        }
        response.getWriter().write("COMPLETED");
    }

    private List<OneDriveFile> getOneDriveFilesList(String accessToken) throws IOException, JSONException {
        List<OneDriveFile> oneDriveFilesList = new ArrayList<>();
        String listChildrenEndpoint = DOCUMENTS_FOLDER_ENDPOINT;
        LOG.debug("GET listChildren Endpoint: {} ", listChildrenEndpoint);

        final HttpGet httpGet = new HttpGet(listChildrenEndpoint);
        httpGet.setHeader(HttpHeaders.ACCEPT, CT_JSON);
        httpGet.setHeader(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
        try (CloseableHttpClient clientPost = HttpClients.createDefault();
             CloseableHttpResponse responseGet = clientPost.execute(httpGet)) {
            LOG.debug("Status Code: {} ", responseGet.getStatusLine().getStatusCode());
            if (responseGet.getStatusLine().getStatusCode() == 200) {
                String apiResponseString = EntityUtils.toString(responseGet.getEntity());
                JSONObject apiResponseJSON = new JSONObject(apiResponseString);
                JSONArray listOfChildren = apiResponseJSON.has("value") ? apiResponseJSON.getJSONArray("value") : null;

                if (nonNull(listOfChildren)) {
                    for (int i = 0; i < listOfChildren.length(); i++) {
                        JSONObject fileDetailsJSON = listOfChildren.getJSONObject(i);
                        OneDriveFile oneDriveFile = new OneDriveFile();
                        oneDriveFile.setName(fileDetailsJSON.getString("name").replaceAll("\\s+", "_"));
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
                    LOG.error("GET listChildren - JSONArray listOfChildren is Null, Check the API response - {}", apiResponseString);
                }
            } else {
                LOG.error("GET listChildren - Response Entity if status code is not 200 : {} ", responseGet.getEntity());
            }
        }
        return oneDriveFilesList;
    }

    private void transferAssetToDAM(OneDriveFile oneDriveFile) {
        LOG.debug("transferAssetToDAM(): {} ", oneDriveFile.getDownloadURL());
        if (oneDriveFile.isFolder()) {
            LOG.debug("transferAssetToDAM() - {} is a Folder", oneDriveFile.getName());
        } else {
            final HttpGet httpGet = new HttpGet(oneDriveFile.getDownloadURL());
            try (CloseableHttpClient clientGet = HttpClients.createDefault();
                 CloseableHttpResponse responseGet = clientGet.execute(httpGet)) {
                if (responseGet.getStatusLine().getStatusCode() == 200) {
                    LOG.error("{} - SUCCESSFULLY DOWNLOADED", oneDriveFile.getName());
                    byte[] oneDriveFileBinaryData = EntityUtils.toByteArray(responseGet.getEntity());

                    HttpPost httpPost = new HttpPost("http://localhost:4502/api/assets/test/" + oneDriveFile.getName());   //folder and filename both should be dynamic
                    String credToEncode = "admin:admin";
                    String encryptedToken = Base64.getEncoder().encodeToString(credToEncode.getBytes());
                    httpPost.setHeader("Authorization", "Basic " + encryptedToken);
                    httpPost.setHeader("Content-Type", oneDriveFile.getMimeType());

                    HttpEntity entity = new ByteArrayEntity(oneDriveFileBinaryData);
                    httpPost.setEntity(entity);

                    try (CloseableHttpClient client = HttpClients.createDefault();
                         CloseableHttpResponse response = client.execute(httpPost)) {

                        if (response.getStatusLine().getStatusCode() == 201) {
                            LOG.error("{} - SUCCESSFULLY UPLOADED", oneDriveFile.getName());
                        } else if (response.getStatusLine().getStatusCode() == 409) {
                            //Conflict -> file already present -> log somehow and proceed to next
                            LOG.error("CONFLICT - File Already Exists at given path!!");
                        } else {
                            //handle error
                            LOG.error("ERROR - AEM Asset upload API response {} - {}", response.getStatusLine().getStatusCode(), EntityUtils.toString(responseGet.getEntity()));
                        }
                    }
                } else {
                    LOG.error("GET downloadFile - Response Entity if status code is not 200 : {} ", responseGet.getEntity());
                }
            } catch (IOException e) {
                LOG.error("GET downloadFile - Exception occurred while trying to execute Favorites API", e);
            }
        }
    }
}
class OneDriveFile {

    private String name;

    private String mimeType;

    private String downloadURL;

    private boolean isFolder;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDownloadURL() {
        return downloadURL;
    }

    public void setDownloadURL(String downloadURL) {
        this.downloadURL = downloadURL;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public void setFolder(boolean folder) {
        isFolder = folder;
    }

}