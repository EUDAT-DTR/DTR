#EUDAT-DTR

Data Type Registry software for the EUDAT project's CDI services

##Setting up a Development Installation

**1. Clone the repository**
```
git clone -b master https://github.com/EUDAT-DTR/DTR.git
cd DTR
```

**2. Run the `autogen.sh` script located in the scripts folder to copy all the DTR development dependencies to their appropriate locations**

```
scripts/autogen.sh
```

**3. Once the unpacking of dependencies is complete, navigate to the `eudat-dtr` directory and build the software**
```
cd cordra/sw/eudat-dtr/
ant war
```
