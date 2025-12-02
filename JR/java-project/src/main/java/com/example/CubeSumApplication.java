package com.example;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Random;

public class CubeSumApplication {
    
    // Размеры 4D куба
    private static final int DIM_SIZE = 200;
    private static final long TOTAL_ELEMENTS = (long) DIM_SIZE * DIM_SIZE * DIM_SIZE * DIM_SIZE;
    
    public static void main(String[] args) {
        System.out.println("=== Кросс-языковое решение: Java + Rust ===");
        System.out.println("Размер 4D куба: %d x %d x %d x %d".formatted(DIM_SIZE, DIM_SIZE, DIM_SIZE, DIM_SIZE));
        System.out.println("Общее количество элементов: %,d".formatted(TOTAL_ELEMENTS));
        System.out.println();
        
        try {
            // Загружаем Rust DLL
            System.loadLibrary("cube_sum");
            
            // 1. Генерация 4D куба
            long startGeneration = System.nanoTime();
            // int[][][][] cube = generate4DCube();
            Random random = new Random();
            int[][][][] cube = new int[DIM_SIZE][DIM_SIZE][DIM_SIZE][DIM_SIZE];
            MemorySegment cubeData;
            try (Arena arena = Arena.ofConfined()) {
                // Выделяем память под все элементы (int = 4 байта)
            cubeData = arena.allocate(TOTAL_ELEMENTS * 4L);         
            int index = 0;
            for (int i = 0; i < DIM_SIZE; i++) {
                for (int j = 0; j < DIM_SIZE; j++) {
                    for (int k = 0; k < DIM_SIZE; k++) {
                        for (int l = 0; l < DIM_SIZE; l++) {
                            cube[i][j][k][l] = random.nextInt();
                            cubeData.setAtIndex(ValueLayout.JAVA_INT, index++, cube[i][j][k][l]);
                        }
                    }
                }
            }
            long endGeneration = System.nanoTime();
            
            System.out.println("[Этап 1] Генерация куба: %.3f сек".formatted((endGeneration - startGeneration) / 1_000_000_000.0));
            
            // 2. Подготовка памяти для передачи в Rust
            long startMemory = System.nanoTime();
            
            // Создаем MemorySegment для хранения всех данных куба
            // MemorySegment cubeData;
            // try (Arena arena = Arena.ofConfined()) {
            //     // Выделяем память под все элементы (int = 4 байта)
            //     cubeData = arena.allocate(TOTAL_ELEMENTS * 4L);
                
            //     // Копируем данные из 4D массива в непрерывный блок памяти
            //     int index = 0;
            //     for (int i = 0; i < DIM_SIZE; i++) {
            //         for (int j = 0; j < DIM_SIZE; j++) {
            //             for (int k = 0; k < DIM_SIZE; k++) {
            //                 for (int l = 0; l < DIM_SIZE; l++) {
            //                     cubeData.setAtIndex(ValueLayout.JAVA_INT, index++, cube[i][j][k][l]);
            //                 }
            //             }
            //         }
            //     }
                
                long endMemory = System.nanoTime();
                System.out.println("[Этап 2] Подготовка памяти: %.3f сек".formatted((endMemory - startMemory) / 1_000_000_000.0));
                
                // 3. Вызов Rust функции
                long startRust = System.nanoTime();
                long sum = callRustSum(cubeData, TOTAL_ELEMENTS);
                long endRust = System.nanoTime();
                
                System.out.println("[Этап 3] Вызов Rust функции: %.3f сек".formatted((endRust - startRust) / 1_000_000_000.0));
                
                // 4. Проверка результата (Java реализация для сравнения)
                long startJavaCheck = System.nanoTime();
                long javaSum = calculateSumInJava(cube);
                long endJavaCheck = System.nanoTime();
                
                System.out.println("[Этап 4] Проверка в Java: %.3f сек".formatted((endJavaCheck - startJavaCheck) / 1_000_000_000.0));
                System.out.println();
                
                // Вывод результатов
                System.out.println("=== Результаты ===");
                System.out.println("Сумма (Rust): %,d".formatted(sum));
                System.out.println("Сумма (Java): %,d".formatted(javaSum));
                System.out.println("Результаты совпадают: " + (sum == javaSum));
                
                long totalTime = (endRust - startGeneration) / 1_000_000;
                System.out.println("Общее время: %,d мс".formatted(totalTime));
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Генерирует 4D куб и заполняет случайными int значениями
     */
    private static int[][][][] generate4DCube() {
        Random random = new Random();
        int[][][][] cube = new int[DIM_SIZE][DIM_SIZE][DIM_SIZE][DIM_SIZE];
        
        for (int i = 0; i < DIM_SIZE; i++) {
            for (int j = 0; j < DIM_SIZE; j++) {
                for (int k = 0; k < DIM_SIZE; k++) {
                    for (int l = 0; l < DIM_SIZE; l++) {
                        cube[i][j][k][l] = random.nextInt();
                    }
                }
            }
        }
        
        return cube;
    }
    
    /**
     * Вызывает Rust функцию для суммирования элементов куба
     */
    private static long callRustSum(MemorySegment data, long length) throws Exception {
        // Загружаем символ из DLL
        SymbolLookup lookup = SymbolLookup.loaderLookup();
        MemorySegment functionSymbol = lookup.find("sum_cube_elements")
                .orElseThrow(() -> new Exception("Функция sum_cube_elements не найдена в DLL"));
        
        // Описываем сигнатуру функции: (pointer, size_t) -> long
        FunctionDescriptor descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,  // возвращаемый тип (i64)
                ValueLayout.ADDRESS,    // указатель на данные
                ValueLayout.JAVA_LONG   // размер (usize)
        );
        
        // Создаем MethodHandle
        Linker linker = Linker.nativeLinker();
        MethodHandle sumFunction = linker.downcallHandle(functionSymbol, descriptor);
        
        // Вызываем функцию
        try {
            return (long) sumFunction.invokeExact(data, (long) length);
        } catch (Throwable e) {
            throw new Exception("Ошибка вызова Rust функции", e);
        }
    }
    
    /**
     * Вычисляет сумму в Java для проверки результата
     */
    private static long calculateSumInJava(int[][][][] cube) {
        long sum = 0;
        for (int i = 0; i < DIM_SIZE; i++) {
            for (int j = 0; j < DIM_SIZE; j++) {
                for (int k = 0; k < DIM_SIZE; k++) {
                    for (int l = 0; l < DIM_SIZE; l++) {
                        sum += cube[i][j][k][l];
                    }
                }
            }
        }
        return sum;
    }
}
