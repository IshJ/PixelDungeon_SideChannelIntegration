package com.watabou.aspects;

import android.util.Log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import com.watabou.MethodStat;

import static com.watabou.JobMainAppInsertRunnable.insert_locker;
import static com.watabou.pixeldungeon.PixelDungeon.fd;
import static com.watabou.pixeldungeon.PixelDungeon.methodIdMap;
import static com.watabou.pixeldungeon.PixelDungeon.methodStats;
import static com.watabou.pixeldungeon.PixelDungeon.readAshMem;

@Aspect
public class AspectLoggingJava {
    static {
        System.loadLibrary("native-lib");
    }

    //    private static final String POINTCUT_METHOD =
//            "execution(* com.watabou.activities.*.*(..))";
    final String POINTCUT_METHOD_MAIN =
            "execution(* com.watabou.pixeldungeon.*.*(..))";

    @Pointcut(POINTCUT_METHOD_MAIN)
    public void executeMain() {
    }

//    @Pointcut("!within(com.watabou.aspects.*.*(..))")
//    public void notAspect() { }


    //    @Pointcut("!within(com.watabou.pixeldungeon.PixelDungeon.*.*(..))")
//    public void notAspectSplashActivity() { }
//
//    @Pointcut("!within(com.watabou.activities.*.onCreate(..))")
//    public void notAspect() { }
//
//    @Around("executeRainViewer() || executeChildA() || executeChildB() || executeChildC()")
//    public Object weaveJoinPoint(ProceedingJoinPoint joinPoint) throws Throwable {
//        try {
//            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
//            methodIdMap.putIfAbsent(methodSignature.toString(), methodIdMap.size());
//
//            long startFd = fd > 0 ? readAshMem(fd) : -1;
//            long startT = System.currentTimeMillis();
//
//            Object result = joinPoint.proceed();
//            long endT = System.currentTimeMillis();
//
//            long endFd = fd > 0 ? readAshMem(fd) : -1;
//
//            MethodStat methodStat = new MethodStat(methodIdMap.get(methodSignature.toString()), startFd, endFd);
//            Log.d("#Aspect ", methodStat.getId()+" "+startFd+" "+endFd+" "+startT+" "+endT);
//            insert_locker.lock();
//            if (methodStats.isEmpty()) {
//                methodStats.add(methodStat);
//
//            } else if (!methodStats.get(methodStats.size() - 1).equals(methodStat)) {
//                methodStats.add(methodStat);
//            }
//            insert_locker.unlock();
//
////            Log.d("LoggingVM ",methodStat.toString());
////            Log.v("LoggingVM ",
////                    methodSignature.toString()+" "+methodSignature.toLongString()+ methodSignature.toShortString());
//            return result;
//        } catch (Exception e) {
//            return joinPoint.proceed();
//        }
//    }

    @Before("executeMain()")
    public void weaveJoinPoint(JoinPoint joinPoint) throws Throwable {
        if (joinPoint == null) {
            return;
        }
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        if(!methodIdMap.containsKey(methodSignature.toString())){

            Log.d("#Aspect ", methodSignature.toString());
        }
        methodIdMap.putIfAbsent(methodSignature.toString(), methodIdMap.size());

        long startFd = fd > 0 ? readAshMem(fd) : -1;
//        long startFd = -1;
//        long endFd = fd > 0 ? readAshMem(fd) : -1;
        long endFd = startFd;
        MethodStat methodStat = new MethodStat(methodIdMap.get(methodSignature.toString()), startFd, endFd);
//        Log.d("#Aspect ", methodStat.getId()+" "+startFd+" "+endFd);

        insert_locker.lock();
        if (methodStats.isEmpty()) {
            methodStats.add(methodStat);

        } else if (!methodStats.get(methodStats.size() - 1).equals(methodStat)) {
            methodStats.add(methodStat);
        }
        insert_locker.unlock();

    }

    public static native long readAshMem1(int fd);
}
