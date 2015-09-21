# wps-gitalgorithm-repository
Algorithm repository for WPS 4.0.0 that clones a git repository and adds available Java processes to the WPS.

## How to use
Simply build the jar file and drop it to the lib directory of your WPS 4.0 installation. 
The repository should appear in the repositories section of the admin application.
There you can edit the following properties:

Property name | Description
------------ | -------------
Remote repository URL | URL of the GitHub repository to check out.
Branch name | The branch name to check out. Future work, not yet implemented.
Filename REGEX  | Only files that fit this REGEX will be used.
Local repository directory | Local directory in which the remote repository will be cloned.
