package com.aem.custom.core.servlets;

import com.aem.custom.core.pojos.OneDriveFile;
import com.aem.custom.core.services.AssetMigration;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.json.JSONException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.*;

@Component(service = {Servlet.class})
@SlingServletPaths(value = "/bin/migration/onedrive")
@ServiceDescription("OneDrive Migration Servlet")
public class OneDriveMigrationServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(OneDriveMigrationServlet.class);

    public static final String SOURCE_PATH = "sourcePath";

    public static final String DESTINATION_PATH = "destinationPath";

    @Reference
    transient AssetMigration assetMigration;

    @Reference
    transient JobManager jobManager;

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        JsonObject responseJSON = new JsonObject();
        try {
            boolean autoSchedule = Boolean.parseBoolean(request.getParameter("autoSchedule"));

            String bSizeString = request.getParameter("batchSize");
            int batchSize = StringUtils.isNotBlank(bSizeString) && StringUtils.isNumeric(bSizeString)
                    ? Integer.parseInt(bSizeString) : 10;

            String sourcePath = StringUtils.isNotBlank(request.getParameter(SOURCE_PATH)) ? request.getParameter(SOURCE_PATH) : StringUtils.EMPTY;
            String destinationPath = StringUtils.isNotBlank(request.getParameter(DESTINATION_PATH)) ? request.getParameter(DESTINATION_PATH) : StringUtils.EMPTY;


            String accessToken = assetMigration.getGraphAPIAccessToken();
            if (StringUtils.isBlank(accessToken)) {
                response.getWriter().write("{\"Error\": \"Graph API access token is empty!\"}");
                return;
            }

            String listChildrenAPIResponse = assetMigration.getListChildrenAPIResponse(accessToken, sourcePath);
            if (StringUtils.isBlank(listChildrenAPIResponse)) {
                response.getWriter().write("{\"Error\": \"listChildrenAPIResponse is empty!\"}");
                return;
            }

            List<OneDriveFile> oneDriveFilesList = assetMigration.extractOneDriveFilesFromResponse(listChildrenAPIResponse);
            if (oneDriveFilesList.isEmpty()) {
                response.getWriter().write("{\"Error\": \"OneDrive Folder is empty!\"}");
                return;
            }

            int startIndex;
            int endIndex;
            int totalFilesCount = oneDriveFilesList.size();

            if (autoSchedule) {
                int numberOfBatches = (int) Math.ceil((double) totalFilesCount / batchSize);
                startIndex = 0;
                endIndex = batchSize - 1;
                for (int i = 0; i < numberOfBatches; i++) {
                    Job job = createOneDriveMigrationJob(startIndex, endIndex, listChildrenAPIResponse, destinationPath);
                    responseJSON.addProperty(startIndex + " - " + endIndex, job.getId());
                    startIndex = endIndex + 1;
                    endIndex = endIndex + batchSize;
                }
            } else {
                LOGGER.debug("OneDriveMigrationServlet: autoSchedule is false");
                String startIndexString = request.getParameter("startIndex");
                startIndex = StringUtils.isNotBlank(startIndexString) && StringUtils.isNumeric(startIndexString)
                        ? Integer.parseInt(startIndexString) : 0;
                endIndex = startIndex + (batchSize - 1);

                if (startIndex >= totalFilesCount) {
                    response.getWriter().write("{\"Error\": \"startIndex - " + startIndex + " is greater than or equal to totalFilesCount - " + totalFilesCount + "\" }");
                    return;
                } else if (startIndex > endIndex){
                    response.getWriter().write("{\"Error\": \"startIndex - " + startIndex + " is greater than endIndex - " + endIndex +  "\" }");
                    return;
                }
                assetMigration.migrateAssetsFromOneDrive(startIndex, endIndex, destinationPath, oneDriveFilesList, responseJSON);
            }

        } catch (JSONException e) {
            LOGGER.error("JSONException occurred while trying to execute Favorites API", e);
        }
        response.getWriter().write(responseJSON.toString());
    }

    /**
     * Creates a Sling job to migrate assets from OneDrive to AEM with given startIndex and endIndex
     * @param startIndex                the index from which files will be uploaded
     * @param endIndex                  the index up to which files will be uploaded
     * @param listChildrenAPIResponse   the listChildrenAPIResponse to be parsed for fetching list of files
     * @param destinationPath           the destinationPath of the files to be uploaded (AEM DAM)
     */
    private Job createOneDriveMigrationJob(int startIndex, int endIndex, String listChildrenAPIResponse, String destinationPath) {
        Map<String, Object> jobProperties = new HashMap<>();
        jobProperties.put("startIndex", startIndex);
        jobProperties.put("endIndex", endIndex);
        jobProperties.put("listChildrenAPIResponse", listChildrenAPIResponse);
        jobProperties.put(DESTINATION_PATH, destinationPath);
        Job job = jobManager.addJob("com/migration/onedrive", jobProperties);

        LOGGER.debug("OneDriveMigrationServlet: Job created from startIndex - {} to endIndex - {}", startIndex, endIndex);
        return job;
    }
}