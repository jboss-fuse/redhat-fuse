# Red Hat Fuse
This project defines the Fuse product BOMs so that Fuse users can have a consistent set of maven bom GAVs to import into their projects regardless of the platform they are using (Karaf, EAP, or SpringBoot).

You just need to add the `<dependencyManagement>..</dependencyManagement>` scection to your maven build to pick up the correct set of Fuse supported artifacts.

Note: we assume that you add the `fuse.version` property to your build and set it to a released version of this project.

## For Karaf

    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>org.jboss.redhat-fuse</groupId>
          <artifactId>fuse-karaf-bom</artifactId>
          <version>${fuse.version}</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>

## For EAP

    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>org.jboss.redhat-fuse</groupId>
          <artifactId>fuse-esp-bom</artifactId>
          <version>${fuse.version}</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>

## For Spring Boot

    <dependencyManagement>
      <dependencies>
        <dependency>
          <groupId>org.jboss.redhat-fuse</groupId>
          <artifactId>fuse-springboot-bom</artifactId>
          <version>${fuse.version}</version>
          <type>pom</type>
          <scope>import</scope>
        </dependency>
      </dependencies>
    </dependencyManagement>



