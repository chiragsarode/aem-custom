package com.aem.custom.core.listeners;

import com.aem.custom.core.services.AssetMigration;
import com.google.gson.JsonObject;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = JobConsumer.class,
        immediate = true,
        property = {
                JobConsumer.PROPERTY_TOPICS + "=" + "com/migration/hdd"
        })

public class HardDriveAssetMigrationJob implements JobConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HardDriveAssetMigrationJob.class);

    @Reference
    AssetMigration assetMigration;


    @Override
    public JobResult process(Job job) {
        try {
            int startIndex = Integer.parseInt(job.getProperty("startIndex").toString());
            int endIndex = Integer.parseInt(job.getProperty("endIndex").toString());
            String sourcePath = job.getProperty("sourcePath").toString();
            String destinationPath = job.getProperty("destinationPath").toString();
            JsonObject responseObject = new JsonObject();
            assetMigration.migrateAssetsFromHDD(startIndex, endIndex, sourcePath, destinationPath, responseObject);
            responseObject.entrySet().forEach(entry ->
                LOG.debug("{} : Exit Code : {}", entry.getKey(), entry.getValue())
            );
            return JobResult.OK;
        } catch (Exception e) {
            LOG.error("Error in Job Consumer :", e);
            return JobResult.FAILED;
        }
    }
}
