# ARCHIVED

This project is no longer maintained and will not receive any further updates. If you plan to continue using it, please be aware that future security issues will not be addressed.

# wps-gitalgorithm-repository
Algorithm repository for a [52°North WPS 4.x](https://github.com/52North/WPS/tree/wps-4.0) that clones a git repository and adds available Java and R processes to the WPS.

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
Branch name | The branch name to check out. Future work, not yet implemented.
Filename REGEX  | Only files that fit this REGEX will be used.
Local repository directory | Local directory in which the remote repository will be cloned.


Currently, you have to restart the WPS to pull from the repository (will change once an option exist which re-initiates a repository once new parameters have been saved).

## Caveats
Do not work on the files checked out by the WPS while it is running. During startup the repository is doing a `git pull` and expects there are no conflicts. As it can't resolve conflicts automatically, it resets to the last commit. In this case you have to pull and resolve conflicts by hand. However, if you won't make changes there won't be conflicts and Pull requests should just work.


Currently, there is a DirectoryWatch on your cloned repository so that changes apply immediately once local changes are detected. This will lead a running WPS to throw exceptions when working on files within the local repository. Again, once the UI can trigger a re-initialize the DirectoryWatcher might become deprecated.

There is an issue with inner classes and packages in Java-processes. These will atm be compiled in a separate file (e.g. ...$1.class) in the same folder as the main class file and if the Java-process is loaded, the inner classes will not be found (the classloader searches in a path following the package-structure of the class). If you have inner classes in your process, you can remove the package as a workaround.
