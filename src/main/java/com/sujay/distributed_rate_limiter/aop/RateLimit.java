package com.sujay.distributed_rate_limiter.aop;

import com.sujay.distributed_rate_limiter.enums.Algorithm;

import java.lang.annotation.*;

//this says that the target must be only on the methods i.e the annotation
@Target(ElementType.METHOD)
// the annotation is available only when the app is running
@Retention(RetentionPolicy.RUNTIME)
// this is only for the doc purposes
@Documented
//  here we are creating our own annotation
public @interface RateLimit {

//     here all are written in method style because we are using this class as an annotation
//     and not treating it like normal class

    Algorithm algorithm() default Algorithm.TOKEN_BUCKET;

    String tier() default "";

    int maxRequests() default 0;

    int windowSeconds() default 0;

//    on what basis are we rate limiting it? enum is given below with all the options
    ClientIdentifier clientIdentifier() default ClientIdentifier.IP_ADDRESS;

    enum ClientIdentifier {
        IP_ADDRESS,
        JWT_SUBJECT,
        API_KEY
    }
}
