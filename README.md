# wps-gitalgorithm-repository
Algorithm repository for [a 52°North WPS 4.0.0](https://github.com/52North/WPS) that clones a git repository and adds available Java and R processes to the WPS.

## Module Integration
Checkout the code via `git clone` and build the repository module via `mvn clean install`. The built `jar` artifact can be found under `target/` folder. Copy and paste it into `${wps-webapp}/WEB-INF/lib` folder of your WPS instance.

## Module Integration for Development
Checkout as described above. Import the module in your favourite IDE. Add
```
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>52n-wps-git-algorithm-repository</artifactId>
    <version>${project.version}</version>
    <scope>runtime</scope>
</dependency>	
```
to the `52n-wps-webapp/pom.xml`. Rebuild the webapp and restart Servlet Container (e.g. Apache Tomcat).

## Configuration
Open the admin UI of your running WPS instance (for example http://localhost:8080/wps/) and login. Go to `Repositories` section and click on **Git Algorithms Repository**. Configure to match your repository and save the changes. 

There you can edit the following properties:

Property name | Description
------------ | -------------
Remote repository URL | URL of the GitHub repository to check out.
Branch name | *Future work, not yet implemented.* The branch name to check out.
Filename REGEX  | Only files that fit this REGEX will be used.
Local repository directory | Local directory to which the remote repository will be cloned. The servlet container user needs write access.


Currently, you have to restart the WPS to pull from the repository (will change once an option exist which re-initiates a repository once new parameters have been saved).

## Caveats
Do not work on the files checked out by the WPS while it is running. During startup the repository is doing a `git pull` and expects there are no conflicts. As it can't resolve conflicts automatically, it resets to the last commit. In this case you have to pull and resolve conflicts by hand. However, if you won't make changes there won't be conflicts and Pull requests should just work.


Currently, there is a DirectoryWatch on your cloned repository so that changes apply immediately once local changes are detected. This will lead a running WPS to throw exceptions when working on files within the local repository. Again, once the UI can trigger a re-initialize the DirectoryWatcher might become deprecated.

