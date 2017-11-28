# EUDAT-DTR — EUDAT's Data Type Registry

The EUDAT-DTR is a user-friendly, secure, and trusted service that allows **researchers, scientfic communities and citizen scientists** to **record, query and disseminate** the Type Descriptions of the data that composes their published datasets. EUDAT-DTR is one of the B2 services developed in the [EUDAT](www.eudat.eu) project.

By using the EUDAT-DTR, both researchers and automatic processes alike can discern the internal structure of a scientific dataset, parse its contents, and understand the assumptions and semantic contexts related to the information contained, like measurement units, reference coordinate systems, variable names or standards used, among others. Moreover, the service allows to add value to research data by assigning Persistent Identifiers ([PIDs](http://www.pidconsortium.eu/)) that ensure long-lasting access and references to the recorded Type Descriptions.

EUDAT-DTR is based on CNRI's [Cordra](https://cordra.org/), a Digital Object Management software that provides facilities for the creation of, and access to, digital information as discrete data structures with unique, resolvable identifiers based on the [Handle System](https://handle.net/).

## Requirements
In order to run EUDAT-DTR, you need to have at least git 1.7, Java 8, access to a running Handle System v7 installation and a valid handle prefix (details can be found at the [Handle System](https://handle.net) web site). Earlier versions might work, but are not supported.

Additionally, EUDAT-DTR needs appropriate credentials to access the Handle System and create new *handle records* on it, so make sure that you have a private key that allows you to do that in the Handle System Server.  

## Installation and configuration
In order to install the latest version of the EUDAT-DTR service, clone a copy of the main EUDAT-DTR Git repository and enter the root directory of the cloned repository:

```console
git clone -b master https://github.com/EUDAT-DTR/DTR.git && cd DTR
```

Now the service needs to be appropriately configured to communicate with a **Handle Server**, which implies creating special handles in the destination Handle Server. Depending on your needs, you may use the automatic `configure-unconfigured-cordra`  script included in the package to provide this information to the service, or configure it manually. Both configuration methods are described below.

### Automatic configuration
For the automatic configuration process, you will need to the following:
- A `private key` that allows the creation of handles in the Handle Server
- A valid `handle prefix` in the Handle Server

For this example, we will assume that the handle prefix allotted to the community is `12345` and that the private key is contained in the `$HOME/12345_privkey.bin` file.

Once you have this information, enter the `cordra` directory and run the `configure-unconfigured-cordra` script:
```console
cd cordra
./configure-unconfigured-cordra
```

The script will now ask for you to enter a `repository handle` name that will be used to create a special handle **in the handle server** that will contain relevant information about EUDAT-DTR (e.g. its access credentials). In our example, we have instructed the script to create the `12345/eudat_dtr` handle:


```console
[ user@hostname: ~/DTR/cordra/ ] $ ./configure-unconfigured-cordra
Your Cordra instance will need to be identified by a handle, its 'repository handle'.
This handle needs to be a handle you control, that is, under a prefix allotted to you.
If you have prefix 12345, the typical repository handle would be 12345/repo


Enter the repository handle you want to create: 12345/eudat_dtr
```

After that, the script will ask you for the identity of your handle server administrator. Typically it will be something similar to `300:0.NA/12345`, which is what we have used for this example:

```console
[ user@hostname: ~/DTR/cordra/ ] $ ./configure-unconfigured-cordra
Your Cordra instance will need to be identified by a handle, its 'repository handle'.
This handle needs to be a handle you control, that is, under a prefix allotted to you.
If you have prefix 12345, the typical repository handle would be 12345/repo


Enter the repository handle you want to create: 12345/eudat_dtr

Enter identity of your handle server administrator (e.g. 300:0.NA/12345): 300:0.NA/12345
```

Now the script will ask for the fully-qualified path to the private key of your handle server administrator, and its passphrase if needed. For the example we are using the private key contained in `$HOME/12345_privkey.bin`, which requires the passphrase `somekey`.

```console
[ user@hostname: ~/DTR/cordra/ ] $ ./configure-unconfigured-cordra
Your Cordra instance will need to be identified by a handle, its 'repository handle'.
This handle needs to be a handle you control, that is, under a prefix allotted to you.
If you have prefix 12345, the typical repository handle would be 12345/repo


Enter the repository handle you want to create: 12345/eudat_dtr

Enter identity of your handle server administrator (e.g. 300:0.NA/12345): 300:0.NA/12345

Enter the fully qualified path to the private key of your handle server administrator: ~/12345_privkey.bin

Enter the passphrase of that private key, if needed (press return if no passphrase): somekey
```

If the information provided is correct, the script will create all the required handles for the EUDAT-DTR service in the Handle Server.

## Setting up a development environment

The latest version of the EUDAT-DTR webapp is already included into the Cordra installation provided in this repository. Nevertheless, if you need to make changes to it and recompile it, the following process is advised.

1. Clone the repository:

```console
git clone -b master https://github.com/EUDAT-DTR/DTR.git
cd DTR
```

2. Run the `autogen.sh` script located in the scripts folder to copy all the DTR development dependencies to their appropriate locations:

```console
scripts/autogen.sh
```

3. Once the unpacking of dependencies is complete, navigate to the `eudat-dtr` directory and build the software:

```console
cd cordra/sw/eudat-dtr/
ant war
```

4. After the build process completes, copy the newly generated `registrar.war` found in `DTR/cordra/sw/eudat-dtr/dist` to `DTR/cordra/data/webapps-priority` making sure to replace the existing `ROOT.war` package:

```console
cd ../../
cp sw/eudat-dtr/dist/registrar.war data/webapps-priority/ROOT.war
```

5. Now the service can be started as usual, as long as the development instance has been configured as explained above:

```console
./startup
```
