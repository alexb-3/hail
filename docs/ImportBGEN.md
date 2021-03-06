# Importing Data from BGEN Files

Hail supports importing data in the BGEN file format. For more information on the BGEN file format, see [here](http://www.well.ox.ac.uk/~gav/bgen_format/bgen_format_v1.1.html). **Only v1.1 BGEN files are supported at this time**.

## Command line options:
Flag | Description
--- | :-: | ---
`-d | --no-compress` | Do not compress VDS.  Not recommended.
`-n <N> | --npartitions <N>` | Number of partitions, advanced user option.
`-t <Double> | --tolerance <Double>` | Given a tolerance of T and the sum of the 3 genotype probabilities equal to X, genotypes with abs(X - 1.0) > T will be filtered out. **\[Default = 0.02\]**.
`-s <file> | --samplefile <file>` | **\[Required\]** File with sample IDs and phenotypes. See the spec [here](http://www.stats.ox.ac.uk/%7Emarchini/software/gwas/file_format.html#Sample_File_Format_).


## Importing BGEN files with the indexbgen and importbgen commands

 - Ensure that the BGEN file(s) and Sample File are correctly prepared for import:
    - Files should reside in the hadoop file system
    - The sample file should have the same number of samples as the BGEN file
    - No duplicate sample IDs are allowed
    
 - Run a hail command first with `indexbgen` and then with `importbgen`.  The below command will first create an index for the .bgen file, then read the .bgen and a .sample files, and lastly write to a .vds file (Hail's preferred format).
``` 
$ hail indexbgen /path/to/file.bgen
$ hail importbgen -s /path/to/file.sample /path/to/file.bgen write -o /path/to/output.vds
```
 
 - To load multiple files at the same time, use [Hadoop glob patterns](https://github.com/broadinstitute/hail/blob/master/docs/ImportGEN.md#hadoopglob):
``` 
$ hail indexbgen /path/to/file.chr*.bgen
$ hail importbgen -s /path/to/file.sample /path/to/file.chr*.bgen write -o /path/to/output.vds
```
 
 - The sample id `s.id` used is the first column in the .sample file
  
## Dosage representation
 - Hail automatically filters out any genotypes where the absolute value of the sum of the dosages is greater than a certain tolerance (specified by `-t` or `--tolerance`) from 1.0. The default value is 0.02.
 - Hail normalizes all dosages to sum to 1.0. Therefore, an input dosage of (0.98, 0.0, 0.0) will be stored as (1.0, 0.0, 0.0) in Hail.
 - Hail will give slightly different results than the original data (maximum difference observed is 3E-4). 

 
## Annotations generated by importbgen
Name | Type | Description
--- | :-: | ---
`va.varid` |   `String` | if a chromosome field is present, the 2nd column of the .gen file (otherwise, the 1st column of the .gen file)
`va.rsid`  |        `String` | if a chromosome field is present, the 3rd column of the .gen file (otherwise, the 2nd column of the .gen file)
