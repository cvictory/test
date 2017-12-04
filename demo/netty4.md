Add the support of netty4 communication module in version 2.5.6 of dubbo. 

At provider：
```xml
<dubbo:protocol server="netty4" />
```

or

```xml
<dubbo:provider server="netty4" />
```

At consumer：
```xml
<dubbo:consumer client="netty4" />

```

> **Note**  
> 1. For different protocols on the provider end, use different network communication frameworks. Please configure multiple protocol and configure that each other.
> 2. The consumer end use this way as follow：
> ```xml
> <dubbo:consumer client="netty">
>   <dubbo:reference />
> </dubbo:consumer>
> ```
> ```xml
> <dubbo:consumer client="netty4">
>   <dubbo:reference />
> </dubbo:consumer>
> ```

> Next we will continue to improve：
> 1. we will provide a reference data for Performance Test Indicators and the performance test that compared with the netty3.
