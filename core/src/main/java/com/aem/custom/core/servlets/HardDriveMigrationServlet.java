package com.aem.custom.core.servlets;


import com.aem.custom.core.services.AssetMigration;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.nonNull;

@Component(service = {Servlet.class})
@SlingServletPaths(value = "/bin/migration/hdd")
@ServiceDescription("HDD Migration Servlet")
public class HardDriveMigrationServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(HardDriveMigrationServlet.class);
    public static final String SOURCE_PATH = "sourcePath";
    public static final String DESTINATION_PATH = "destinationPath";

    @Reference
    transient JobManager jobManager;

    @Reference
    transient AssetMigration assetMigration;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {

        boolean autoSchedule = Boolean.parseBoolean(request.getParameter("autoSchedule"));

        String bSizeString = request.getParameter("batchSize");
        int batchSize = StringUtils.isNotBlank(bSizeString) && StringUtils.isNumeric(bSizeString)
                ? Integer.parseInt(bSizeString) : 10;

        String sourcePath = StringUtils.isNotBlank(request.getParameter(SOURCE_PATH)) ? request.getParameter(SOURCE_PATH) : StringUtils.EMPTY;
        File sourceFolder = new File(sourcePath);
        if (!sourceFolder.exists()) {
            response.getWriter().write("{\"Error\": \"Source Path does not exist\"}");
            return;
        }

        String destinationPath = StringUtils.isNotBlank(request.getParameter(DESTINATION_PATH)) ? request.getParameter(DESTINATION_PATH) : StringUtils.EMPTY;
        //todo: can check for ResourceUtil.isNonExistingResource but have to use user's resolver

        int totalFilesCount = getTotalFilesCount(sourceFolder);
        if (totalFilesCount == 0 || totalFilesCount == -1) {
            response.getWriter().write("{\"Error\": \"Folder is Empty / List of files is null\"}");
            return;
        }
        LOGGER.debug("Migration Parameters:\n autoSchedule - {}\n batchSize - {}\n totalFilesCount - {}\n sourcePath - {}\n destinationPath - {}", autoSchedule, batchSize, totalFilesCount, sourcePath, destinationPath);

        int startIndex;
        int endIndex;
        JsonObject responseObject = new JsonObject();

        if (autoSchedule) {
            LOGGER.debug("HardDriveMigrationServlet: autoSchedule is true");
            int numberOfBatches = (int) Math.ceil((double) totalFilesCount / batchSize);
            startIndex = 0;
            endIndex = batchSize - 1;
            for (int i = 0; i < numberOfBatches; i++) {
                Job job = createFileUploadJob(startIndex, endIndex, sourcePath, destinationPath);
                responseObject.addProperty(startIndex + " - " + endIndex, job.getId());
                startIndex = endIndex + 1;
                endIndex = endIndex + batchSize;
            }

        } else {
            LOGGER.debug("HardDriveMigrationServlet: autoSchedule is false");
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
            assetMigration.migrateAssetsFromHDD(startIndex, endIndex, sourcePath, destinationPath, responseObject);
        }
        response.getWriter().write(responseObject.toString());
    }

    /**
     * Creates a Sling job to upload assets to AEM with given startIndex and endIndex
     * @param startIndex      the index from which files will be uploaded
     * @param endIndex        the index up to which files will be uploaded
     * @param sourcePath      the sourcePath of the files to be uploaded
     * @param destinationPath the destinationPath of the files to be uploaded
     */
    private Job createFileUploadJob(int startIndex, int endIndex, String sourcePath, String destinationPath) {
        Map<String, Object> jobProperties = new HashMap<>();
        jobProperties.put("startIndex", startIndex);
        jobProperties.put("endIndex", endIndex);
        jobProperties.put(SOURCE_PATH, sourcePath);
        jobProperties.put(DESTINATION_PATH, destinationPath);
        Job job = jobManager.addJob("com/migration/hdd", jobProperties);

        LOGGER.debug("HardDriveMigrationServlet: Job created from startIndex - {} to endIndex - {}", startIndex, endIndex);
        return job;
    }

    /**
     * Returns the total number of files present in the source folder
     * @return total number of files
     */
    private int getTotalFilesCount(File sourceFolder) {
        File[] listOfFiles = sourceFolder.listFiles();
        return nonNull(listOfFiles) ? listOfFiles.length : -1;
    }
}
