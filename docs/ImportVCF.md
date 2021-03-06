## VCF Files

 * Hail is designed to be maximally compatible with files in the [VCF v4.2 spec](https://samtools.github.io/hts-specs/VCFv4.2.pdf).
 * Hail makes certain assumptions about the genotype fields, see [Representation](https://github.com/broadinstitute/hail/blob/master/docs/Representation.md).  On import, Hail filters (sets to no-call) any genotype that violates these assumptions.  
 * Hail interpets the format fields: GT, AD, OD, DP, GQ, PL; all others are silently dropped.
 * When using [Hadoop glob patterns](https://github.com/broadinstitute/hail/blob/master/docs/ImportGEN.md#hadoopglob), all files must have the same header (VCF files) and the same set of samples in the same order (e.g., a dataset split by chromosome).

### Command line options:

Flag | Description | Default
--- | :-: | ---
`--skip-genotypes` | Do not load genotypes, creates a sites-only VDS | False
`-d | --no-compress` | Do not compress VDS. | False (Not recommended)
`-f | --force` | Force load `.gz` file.  | False (Not recommended -- see below).
`--header-file <file>` | File to load VCF header from | `importvcf` reads the header from the first file listed.
`-n <N> | --npartitions <N>` | Number of partitions | Advanced user option.
`--store-gq` | Store GQ rather than computing it from PL |  Intended for use with the Michigan GotCloud calling pipeline which stores PLs but sets the GQ to the quality of the posterior probabilities.  Disables the GQ representation checks (GQ present iff PL present, GQ the difference of two smallest PL entries).  This option is experimental and will be removed when Hail supports posterior probabilities (PP).
`--pp-as-pl` | Take the genotype PP field instead of PL as Hail PLs. | Experimental, probably slow.
`--skip-bad-ad` | Store as missing all AD fields with an invalid number of elements, instead of throwing an error. |

### Importing VCF files with the importvcf command

 - Ensure that the VCF file is correctly prepared for import:
    - VCFs should be either uncompressed (".vcf") or block-compressed (".vcf.bgz").  If you have a large compressed VCF that ends in ".vcf.gz", it is likely that the file is actually block-compressed, and you should rename the file to ".vcf.bgz" accordingly.  If you actually have a standard gzipped file, it is possible to import it to hail using the `-f` option.  However, this is not recommended -- all parsing will have to take place on one node, because gzip decompression is not parallelizable.  In this case, import could take *significantly* longer.
    - VCFs should reside in the hadoop file system
 
 - Run a hail command with `importvcf`.  The below command will read a .vcf.bgz file and write to a .vds file (Hail's preferred format). 
``` 
$ hail importvcf /path/to/file.vcf.bgz write -o /path/to/output.vds
```
 
 - Hail makes certain assumptions about the genotype fields, see [Representation](https://github.com/broadinstitute/hail/blob/master/docs/Representation.md).  On import, Hail filters (sets to no-call) any genotype that violates these assumptions.

<a name="annotations"></a>
### Annotations generated by importvcf
Name | Type | Description
--- | :-: | ---
`va.pass` |          `Boolean` | true if the variant contains `PASS` in the filter field (false if `.` or other)
`va.filters`|   `Set[String]` | set containing the list of filters applied to a variant.  Accessible using `va.filters.contains("VQSRTranche99.5...")`, for example
`va.rsid`|          `String` | rsid of the variant, if it has one ("." otherwise)
`va.qual`|           `Double` | the number in the qual field
`va.info.<field>` |        `T` | matches (with proper capitalization) any defined info field.  Data types match the type specified in the vcf header, and if the `Number` is "A", "R", or "G", the result will be stored in an array (accessed with array\[index\]).
