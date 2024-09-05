package com.aem.custom.core.listeners;

import com.aem.custom.core.pojos.OneDriveFile;
import com.aem.custom.core.services.AssetMigration;
import com.google.gson.JsonObject;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

@Component(service = JobConsumer.class,
        immediate = true,
        property = {
                JobConsumer.PROPERTY_TOPICS + "=" + "com/migration/onedrive"
        })

public class OneDriveAssetMigrationJob implements JobConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneDriveAssetMigrationJob.class);

    @Reference
    AssetMigration assetMigration;


    @Override
    public JobResult process(Job job) {
        try {
            int startIndex = Integer.parseInt(job.getProperty("startIndex").toString());
            int endIndex = Integer.parseInt(job.getProperty("endIndex").toString());
            String listChildrenAPIResponse = job.getProperty("listChildrenAPIResponse").toString();
            String destinationPath = job.getProperty("destinationPath").toString();

            List<OneDriveFile> oneDriveFilesList = assetMigration.extractOneDriveFilesFromResponse(listChildrenAPIResponse);

            JsonObject responseJSON = new JsonObject();
            assetMigration.migrateAssetsFromOneDrive(startIndex, endIndex, destinationPath, oneDriveFilesList, responseJSON);
            responseJSON.entrySet().forEach(entry ->
                    LOGGER.debug("{} : Status : {}", entry.getKey(), entry.getValue())
            );
            return JobResult.OK;
        } catch (Exception e) {
            LOGGER.error("Error in Job Consumer :", e);
            return JobResult.FAILED;
        }
    }
}
