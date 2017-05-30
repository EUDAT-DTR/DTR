function ReplicationEditor(containerDiv, remoteRepositories, replicationCredentials) {
    var self = this;
    
    function constructor() {
        var remoteRepositoriesDiv = $('<div class="well"></div>');
        containerDiv.append(remoteRepositoriesDiv);
        var replicationCredentialsDiv = $('<div class="well"></div>');
        containerDiv.append(replicationCredentialsDiv);
        
        var remoteRepositoriesEditor = new RemoteRepositoriesEditor(remoteRepositoriesDiv, remoteRepositories);
        var replicationCredentialsEditor = new ReplicationCredentialsEditor(replicationCredentialsDiv, replicationCredentials);
    }
    
    constructor();
}