package com.aem.custom.core.services.impl;

import com.aem.custom.core.config.AssetMigrationConfiguration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = AssetMigrationConfigurationImpl.class)
@Designate(ocd = AssetMigrationConfiguration.class)
public class AssetMigrationConfigurationImpl {
    private String userId;
    private String userPassword;
    private String aemURL;
    private String bearerToken;
    private Boolean isThisAEMaaCS;


    public boolean isThisAEMaaCS() {
        return isThisAEMaaCS;
    }


    public String getUserId() {
        return userId;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public String getAemURL() {
        return aemURL;
    }

    public String getBearerToken() {
        return bearerToken;
    }


    @Activate
    @Modified
    protected void activate(AssetMigrationConfiguration config) {
        this.userId = config.getUserId();
        this.bearerToken = config.getBearerToken();
        this.userPassword = config.getUserPassword();
        this.aemURL = config.getAemURL();
        this.isThisAEMaaCS = config.isThisAEMaaCS();
    }

}
