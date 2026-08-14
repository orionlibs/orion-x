window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;


$(document).ready(function()
{
    orionCommon.makeGetAJAXCall('/api/configuration/types/repository-provider-api', configurationPage.loadRepoProviderAPIConfiguration);
    orionCommon.makeGetAJAXCall('/api/configuration/types/repository-provider-pipeline', configurationPage.loadRepoProviderPipelineConfiguration);
    orionCommon.makeGetAJAXCall('/api/configuration/types/slack', configurationPage.loadSlackConfiguration);
    orionCommon.makeGetAJAXCall('/api/configuration/types/emailer', configurationPage.loadEmailerConfiguration);
    orionCommon.makeGetAJAXCall('/api/configuration/types/sla', configurationPage.loadSLAConfiguration);
    orionCommon.makeGetAJAXCall('/api/configuration/types/wiki-provider-api', configurationPage.loadWikiProviderAPIConfiguration);

    $('body').on('click', '#show-repo-provider-api-configuration-tab-content-button', function(e)
    {
        $("#repo-provider-api-configuration-tab-content").removeClass("hidden");
        $("#show-repo-provider-api-configuration-tab-content-button").addClass("active");
        $("#repo-provider-pipeline-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-pipeline-configuration-tab-content-button").removeClass("active");
        $("#slack-configuration-tab-content").addClass("hidden");
        $("#show-slack-configuration-tab-content-button").removeClass("active");
        $("#emailer-configuration-tab-content").addClass("hidden");
        $("#show-emailer-configuration-tab-content-button").removeClass("active");
        $("#sla-configuration-tab-content").addClass("hidden");
        $("#show-sla-configuration-tab-content-button").removeClass("active");
        $("#wiki-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-wiki-provider-api-configuration-tab-content-button").removeClass("active");
    });


    $('body').on('click', '#show-repo-provider-pipeline-configuration-tab-content-button', function(e)
    {
        $("#repo-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-api-configuration-tab-content-button").removeClass("active");
        $("#repo-provider-pipeline-configuration-tab-content").removeClass("hidden");
        $("#show-repo-provider-pipeline-configuration-tab-content-button").addClass("active");
        $("#slack-configuration-tab-content").addClass("hidden");
        $("#show-slack-configuration-tab-content-button").removeClass("active");
        $("#emailer-configuration-tab-content").addClass("hidden");
        $("#show-emailer-configuration-tab-content-button").removeClass("active");
        $("#sla-configuration-tab-content").addClass("hidden");
        $("#show-sla-configuration-tab-content-button").removeClass("active");
        $("#wiki-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-wiki-provider-api-configuration-tab-content-button").removeClass("active");
    });


    $('body').on('click', '#show-slack-configuration-tab-content-button', function(e)
    {
        $("#repo-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-api-configuration-tab-content-button").removeClass("active");
        $("#repo-provider-pipeline-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-pipeline-configuration-tab-content-button").removeClass("active");
        $("#slack-configuration-tab-content").removeClass("hidden");
        $("#show-slack-configuration-tab-content-button").addClass("active");
        $("#emailer-configuration-tab-content").addClass("hidden");
        $("#show-emailer-configuration-tab-content-button").removeClass("active");
        $("#sla-configuration-tab-content").addClass("hidden");
        $("#show-sla-configuration-tab-content-button").removeClass("active");
        $("#wiki-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-wiki-provider-api-configuration-tab-content-button").removeClass("active");
    });


    $('body').on('click', '#show-emailer-configuration-tab-content-button', function(e)
    {
        $("#repo-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-api-configuration-tab-content-button").removeClass("active");
        $("#repo-provider-pipeline-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-pipeline-configuration-tab-content-button").removeClass("active");
        $("#slack-configuration-tab-content").addClass("hidden");
        $("#show-slack-configuration-tab-content-button").removeClass("active");
        $("#emailer-configuration-tab-content").removeClass("hidden");
        $("#show-emailer-configuration-tab-content-button").addClass("active");
        $("#sla-configuration-tab-content").addClass("hidden");
        $("#show-sla-configuration-tab-content-button").removeClass("active");
        $("#wiki-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-wiki-provider-api-configuration-tab-content-button").removeClass("active");
    });


    $('body').on('click', '#show-sla-configuration-tab-content-button', function(e)
    {
        $("#repo-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-api-configuration-tab-content-button").removeClass("active");
        $("#repo-provider-pipeline-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-pipeline-configuration-tab-content-button").removeClass("active");
        $("#slack-configuration-tab-content").addClass("hidden");
        $("#show-slack-configuration-tab-content-button").removeClass("active");
        $("#emailer-configuration-tab-content").addClass("hidden");
        $("#show-emailer-configuration-tab-content-button").removeClass("active");
        $("#sla-configuration-tab-content").removeClass("hidden");
        $("#show-sla-configuration-tab-content-button").addClass("active");
        $("#wiki-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-wiki-provider-api-configuration-tab-content-button").removeClass("active");
    });


    $('body').on('click', '#show-wiki-provider-api-configuration-tab-content-button', function(e)
    {
        $("#repo-provider-api-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-api-configuration-tab-content-button").removeClass("active");
        $("#repo-provider-pipeline-configuration-tab-content").addClass("hidden");
        $("#show-repo-provider-pipeline-configuration-tab-content-button").removeClass("active");
        $("#slack-configuration-tab-content").addClass("hidden");
        $("#show-slack-configuration-tab-content-button").removeClass("active");
        $("#emailer-configuration-tab-content").addClass("hidden");
        $("#show-emailer-configuration-tab-content-button").removeClass("active");
        $("#sla-configuration-tab-content").addClass("hidden");
        $("#show-sla-configuration-tab-content-button").removeClass("active");
        $("#wiki-provider-api-configuration-tab-content").removeClass("hidden");
        $("#show-wiki-provider-api-configuration-tab-content-button").addClass("active");
    });


    $('body').on('click', '#update-repo-provider-api-configuration-button', function(e)
    {
        e.preventDefault();
        const dataToSend = {
          configurationProperties: {
            "repository.provider.api.key": $("#input-repo-provider-api-key").val(),
            "repository.provider.api.username": $("#input-repo-provider-username").val(),
            "repository.provider.api.base.url": $("#input-repo-provider-api-base-url").val()
          }
        };
        orionCommon.makePutAJAXCall('/api/configuration/types/repository-provider-api', dataToSend, configurationPage.processConfigurationUpdate, configurationPage.processConfigurationUpdate);
    });


    $('body').on('click', '#update-repo-provider-pipeline-configuration-button', function(e)
    {
        e.preventDefault();
        const dataToSend = {
            configurationProperties: {
                "commit.api.url.prefix": $("#input-repo-provider-commit-api-url-prefix").val(),
                "pipeline.url.prefix": $("#input-repo-provider-pipeline-url-prefix").val(),
                "pipeline.api.url.prefix": $("#input-repo-provider-pipeline-api-url-prefix").val()
            }
        };
        orionCommon.makePutAJAXCall('/api/configuration/types/repository-provider-pipeline', dataToSend, configurationPage.processConfigurationUpdate, configurationPage.processConfigurationUpdate);
    });


    $('body').on('click', '#update-slack-configuration-button', function(e)
    {
        e.preventDefault();
        const dataToSend = {
            configurationProperties: {
                "slack.channel.webhook.url": $("#input-slack-channel-webhook-url").val()
            }
        };
        orionCommon.makePutAJAXCall('/api/configuration/types/slack', dataToSend, configurationPage.processConfigurationUpdate, configurationPage.processConfigurationUpdate);
    });


    $('body').on('click', '#update-emailer-configuration-button', function(e)
    {
        e.preventDefault();
        const dataToSend = {
            configurationProperties: {
                "emailer.username": $("#input-emailer-username").val(),
                "emailer.password": $("#input-emailer-password").val(),
                "emailer.enabled": $("#input-emailer-enabled").val()
            }
        };
        orionCommon.makePutAJAXCall('/api/configuration/types/emailer', dataToSend, configurationPage.processConfigurationUpdate, configurationPage.processConfigurationUpdate);
    });


    $('body').on('click', '#update-sla-configuration-button', function(e)
    {
        e.preventDefault();
        const dataToSend = {
            configurationProperties: {
                "sla.time-to-fix-in-minutes": $("#input-sla-time-to-fix-in-minutes").val()
            }
        };
        orionCommon.makePutAJAXCall('/api/configuration/types/sla', dataToSend, configurationPage.processConfigurationUpdate, configurationPage.processConfigurationUpdate);
    });


    $('body').on('click', '#update-wiki-provider-api-configuration-button', function(e)
    {
        e.preventDefault();
        const dataToSend = {
            configurationProperties: {
                "wiki.provider.api.key": $("#input-wiki-provider-api-key").val(),
                "wiki.provider.api.username": $("#input-wiki-provider-username").val(),
                "wiki.provider.api.base.url": $("#input-wiki-provider-api-base-url").val()
            }
        };
        orionCommon.makePutAJAXCall('/api/configuration/types/wiki-provider-api', dataToSend, configurationPage.processConfigurationUpdate, configurationPage.processConfigurationUpdate);
    });


    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let configurationPage =
{
    loadRepoProviderAPIConfiguration : function(response)
    {
        $("#input-repo-provider-api-key").val(response.data.configurationProperties['repository.provider.api.key']);
        $("#input-repo-provider-username").val(response.data.configurationProperties['repository.provider.api.username']);
        $("#input-repo-provider-api-base-url").val(response.data.configurationProperties['repository.provider.api.base.url']);
    },


    loadRepoProviderPipelineConfiguration : function(response)
    {
        $("#input-repo-provider-commit-api-url-prefix").val(response.data.configurationProperties['commit.api.url.prefix']);
        $("#input-repo-provider-pipeline-url-prefix").val(response.data.configurationProperties['pipeline.url.prefix']);
        $("#input-repo-provider-pipeline-api-url-prefix").val(response.data.configurationProperties['pipeline.api.url.prefix']);
    },


    loadSlackConfiguration : function(response)
    {
        $("#input-slack-channel-webhook-url").val(response.data.configurationProperties['slack.channel.webhook.url']);
    },


    loadEmailerConfiguration : function(response)
    {
        $("#input-emailer-username").val(response.data.configurationProperties['emailer.username']);
        $("#input-emailer-password").val(response.data.configurationProperties['emailer.password']);
        $("#input-emailer-enabled").val(response.data.configurationProperties['emailer.enabled']);
    },


    loadSLAConfiguration : function(response)
    {
        $("#input-sla-time-to-fix-in-minutes").val(response.data.configurationProperties['sla.time-to-fix-in-minutes']);
    },


    loadWikiProviderAPIConfiguration : function(response)
    {
        $("#input-wiki-provider-api-key").val(response.data.configurationProperties['wiki.provider.api.key']);
        $("#input-wiki-provider-username").val(response.data.configurationProperties['wiki.provider.api.username']);
        $("#input-wiki-provider-api-base-url").val(response.data.configurationProperties['wiki.provider.api.base.url']);
    },


    processConfigurationUpdate : function(response)
    {
        orionCommon.showNotification("Saved!", response.data.message, 3000);
    }
};