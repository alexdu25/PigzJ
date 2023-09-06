# PigzJ - Parallel Implementation Gzip Java
Java implementation of pigz, a multithreaded Gzip

Pigz is a multithreaded version of Gzip that is implemented in C. We want to
emulate this using Java.

I started by trying to understand the big picture of what the Pigz was 
doing. First, we want to process the data and parse a "-p" argument 
appropriately, if necessary. Then, we want to spawn specified number of threads 
on a buffer from the data that is split into chunks of BLOCK_SIZE. We then 
append the results of each of these threads into a list, and we are reader to 
produce our output. The format is a header, then the list we just created, and 
finally a trailer.

Below is a rundown of my implementation:

First, I split my program into 2 files: compress and pigzj, the latter would 
contain the main function. The reason for this was so that my compress object 
could implement Callable. This is preferable to Runnable because Callable can 
return something (in this case, a byte array that returns the compressed data). 
When combining this with a executor submit call from the main in pigzj, we can 
store the results of the multithreaded computation in a Future object (in our 
case, a simple list will suffice).

In the main function, I use a bunch of try and catch statements to try and 
catch errors. Some notable ones is if -p is followed by an appropriate value: 
-p should be followed by a number that is less than or equal to four times the 
amount of processors available. If this is the case, we set the number of 
processors to be equal to this number; otherwise, we print an error. If there's 
no argument after "-p" either, we should also return an error.

Next, we reset the CRC32 and begin the multithreading. I used Java's 
ExecutorService concurrency utility -- I feed the executor the fixed thread 
value that was given either by the "-p" argument or just simply the number of 
processors, then proceed to splitting the data into blocks.
The buffer stream continuously splits the stream into blocks of 128kb, and 
these buffers are fed into our executor through a submit call after we 
initialize a thread. We have a small check for if the size of the current block 
from the stream is less than BLOCK_SIZE to adjust accordingly for the final 
block parsed. I store all of the returned Future objects in a list, which would 
be used later to contribute to the output.
Additionally, we make sure to keep a running count of total length of buffer, 
since this is necessary for writing the trailer.

When we use the executor submit call on our Compress object, we expect a Future 
object denoting the compressed data for a specific block. Our compress object 
does this by implementing Callable (returning a byte list). Aside from storing 
block size, our compress object needs to store the current buffer for the block 
as well as the dictionary for previous block, per spec.
Since we implemented Callable, the interface requires us to implement its 
call() function, which should return the desired byte list. We declare the 
deflator and feed the input as the bytes that were set on creation of the 
object.
We then set the dictionary to be the dictionary buffer that was set on creation 
of the object.

Now that we have finished the bulk of the compression, we have to write the 
header and trailer. My header and trailer both used the provided logic given by 
MessAdmin, only trimming the parameters slightly (header used the magic number 
appropriately and trailer used the same writeshort->writeint->bytelist logic as 
the original code). We use a try catch to report any possible errors when 
writing.

Runtime Analysis:

GZIP
time gzip <$input >gzip.gz
real    0m9.355s
user    0m9.047s
sys     0m0.165s

PIGZ
time pigz <$input >pigz.gz
real    0m2.477s
user    0m9.204s
sys     0m0.121s

PIGZJ
time java Pigzj <$input >Pigzj.gz
real    0m3.076s
user    0m10.811s
sys     0m0.458s

Multithreading Benchmarks

time java Pigzj -p 1 <$input >Pigzj.gz
real    0m8.982s
user    0m10.130s
sys     0m0.326s

time java Pigzj -p 4 <$input >Pigzj.gz
real    0m3.166s
user    0m11.097s
sys     0m0.502s

time java Pigzj -p 8 <$input >Pigzj.gz
real    0m3.346s
user    0m10.942s
sys     0m0.375s

time java Pigzj -p 16 <$input >Pigzj.gz
real    0m3.929s
user    0m11.627s
sys     0m0.280s

time java Pigzj -p 24 <$input >Pigzj.gz
too many processors

When examining the performance of our program vs GZIP, it is obvious our 
program is much faster. PIGZJ can compete with PIGZ, although pigz has an 
edge (C is slightly faster anyways).
