$(document).ready(function()
{
    $('header').on('click', '#update-fix-timestamp-of-failed-test-runs', function(e)
    {
        orionCommon.makePutAJAXCall('/api/repositories/pipelines/next-successful-status?numberOfDays=5', null, headerPage.updateFixTimestampOfFailedTestRuns);
    });


    $('header').on('click', '#update-repos-existence-of-e2e-tests', function(e)
    {
        orionCommon.makePutAJAXCall('/api/repositories/e2e-test-existence', null, headerPage.updateExistenceOfE2ETestsForAllRepos);
    });
});


let headerPage =
{
    updateFixTimestampOfFailedTestRuns : function(response)
    {
        orionCommon.showNotification('Update!', response.data.message, 6000);
    },


    updateExistenceOfE2ETestsForAllRepos : function(response)
    {
        orionCommon.showNotification('Update!', response.data.message, 3000);
    }
};