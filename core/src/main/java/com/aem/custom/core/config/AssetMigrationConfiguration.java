package com.aem.custom.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Asset Migration Configuration",
        description = "Asset Migration Configuration Details"
)
public @interface AssetMigrationConfiguration {

    /**
     * Method will that it is AEMaaCS or AEM Premise
     */
    @AttributeDefinition(name = "Is this AEMaaCS?", description = "Is this a AEM as Cloud Service?")
    boolean isThisAEMaaCS() default false;

    /**
     * Method will return User ID
     */
    @AttributeDefinition(name = "User ID", description = "Enter the User ID")
    String getUserId();

    /**
     * Method will return Password
     */
    @AttributeDefinition(name = "Password", description = "Enter the Password")
    String getUserPassword();

    /**
     * Method will Bearer Token
     */
    @AttributeDefinition(name = "Bearer Token for AEMaaCS", description = "Enter the Bearer Token for AEMaaCS")
    String getBearerToken();

    /**
     * Method will return the AEM URL
     */
    @AttributeDefinition(name = "AEM URL", description = "Enter AEM URL")
    String getAemURL();
}

