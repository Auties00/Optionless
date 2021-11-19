# Optional Desugarer
Desugar optional calls for a null safe type system with no performance hits

### What and Why
One month ago, I stumbled upon an [interesting article](https://www.reddit.com/r/rust/comments/q99eqe/rust_option_30x_more_efficient_to_return_than/) on r/java, reposted from r/rust.
In short, the article compares the performance of the Optional wrapper in Java with Rust's equivalent. As shown in the article, the performance hit of Optional is considerable, though
it has been getting better with every JDK release. I was intrigued with the idea of resolving this issue and, considering that I had just finished working on [Reified](https://github.com/Auties00/reified) for Java,
I figured that the issue could be fixed through AST manipulation. And after one month of work, I think that this desugarer is ready to be tested. This is not really intended to be used in production,
as it's more of a project to test the boundaries of the language and a challenge for myself, perhaps though with enough contribution it might become a handy tool. 
If you want to observe how the plugin modifies the AST, you can decompile the compiled classes using IntelliJ's built-in decompiler or any other standalone one such as JD GUI.

### Benchmarks
To verify the efficiency of this project, I have run a number of benchmarks using as a model the code from the original article.ù
The source code and the full log of each benchmark can be found inside the [benchmark folder](https://github.com/Auties00/OptionalDesugarer/tree/master/benchmarks) in this repository.
All tests were run using JDK 17, for more information check the above-mentioned folder logs.

Code from the article without the plugin __(WORST)__ \
The code used by the author of the original article without using this plugin
```
Benchmark                    Mode  Cnt     Score     Error  Units
OptionBenchmark.sumNulls     avgt    5   719,811 ±   2,310  us/op
OptionBenchmark.sumOptional  avgt    5  3109,123 ±  39,577  us/op
OptionBenchmark.sumSimple    avgt    5   342,732 ±  12,183  us/op
```

Code from the article with the plugin \
The code used by the author of the original article using this plugin
```
Benchmark                    Mode  Cnt    Score    Error  Units
OptionBenchmark.sumNulls     avgt    5  707,844 ±  6,488  us/op
OptionBenchmark.sumOptional  avgt    5  746,546 ±  6,932  us/op
OptionBenchmark.sumSimple    avgt    5  340,930 ± 11,849  us/op
```

Code from the article with optimized getOptionalNumber and plugin __(BEST)__ \
The code used by the author with a refactor of the getOptionalNumber method to improve efficiency and the plugin
```
Benchmark                    Mode  Cnt    Score    Error  Units
OptionBenchmark.sumNulls     avgt    5  719,530 ±  3,612  us/op
OptionBenchmark.sumOptional  avgt    5  338,803 ±  2,524  us/op
OptionBenchmark.sumSimple    avgt    5  339,972 ± 11,211  us/op
```

Code from the article with optimized sumOptional, for loop replaced with LongStream, and plugin \
The code used by the author with a refactor of the getOptionalNumber method to improve efficiency, a LongStream implementation of the sumOptional method, and the plugin
```
Benchmark                    Mode  Cnt    Score    Error  Units
OptionBenchmark.sumNulls     avgt    5  772,831 ±  9,565  us/op
OptionBenchmark.sumOptional  avgt    5  373,321 ±  8,012  us/op
OptionBenchmark.sumSimple    avgt    5  340,566 ± 13,119  us/op
```

### How to install
Installing the plugin is pretty easy, all you need to do is add a dependency to your project.

#### Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.github.auties00</groupId>
        <artifactId>optional</artifactId>
        <version>1.0</version>
    </dependency>
</dependencies>
```

#### Gradle
```groovy
implementation 'com.github.auties00:optional:1.0'
```

#### Gradle Kotlin DSL
```groovy
implementation("com.github.auties00:optional:1.0")
```

### Options
To pass an option to the plugin, append the following argument when compiling:
```
-Xplugin:Optional [options]
```

This can obviously be done also from within Maven and Gradle.

The plugin supports two options:
1. skip\
   If for whatever reason you want to skip the desugaring process

2. debug\
   Prints the desugared classes to the console when compiling

Any number of options can be specified using an empty space as separator.
