import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import it.unimi.dsi.fastutil.BigArrays;

public class SimpleTest {
    public static void main(String[] args) {
        long initialCapacity = 1024;
        final ByteBigArrayBigList buffer = new ByteBigArrayBigList(initialCapacity);;
        var i = it.unimi.dsi.fastutil.BigArrays.length(buffer.elements());
        System.out.println("buffer size: "+ buffer.size64() + " V:" + i);
         EfficientBigByteList list = new EfficientBigByteList();
        long t1 = System.currentTimeMillis();
        // Добавляем 5 млн элементов — перераспределение произойдёт только 5 раз
        for (long i1 = 0; i1 < 2_000_000_000L; i1++) {
            list.add((byte)(i1 % 128));
            // if(i1%1000000L == 0L)
            //    System.out.println("Расширяю размер: " + i1);
        }
        
        System.out.println("Финальный размер: " + list.size());
        System.out.println("Финальная ёмкость: " + list.capacity());

        // 15_959_326_720
        long t2 = System.currentTimeMillis();
        System.out.println("Время заполнения: " + (t2-t1));
        t1 = System.currentTimeMillis();
        byte[] b = list.toByteArray();
        t2 = System.currentTimeMillis();
        System.out.println("Время toByteArray: " + (t2-t1) + " Size array: " + b.length + ""+ " sample 987654: " + b[987654]);
        // System.out.readln();

   }

//    public byte[] toByteArrayM() {
      
//       byte[][] bigArray = list.toBigArray();
//       return bigArray[0];
//    }
}

public class EfficientBigByteList {
    private final ByteBigArrayBigList buffer;
    private final long CHUNK_SIZE =  100*1024 * 1024L; // 1МБ чанк
    private long currentCapacity;

    public EfficientBigByteList() {
        this.buffer = new ByteBigArrayBigList(5_000_000_000L);
        this.currentCapacity = CHUNK_SIZE;
    }

    public void add(byte value) {
        // Проверяем, достигли ли лимита
        if (buffer.size64() >= currentCapacity) {
            currentCapacity += 10_000;
            buffer.ensureCapacity(currentCapacity); // Расширяем ЗАРАНЕЕ
            // System.out.println("Расширяю ёмкость до: " + currentCapacity);
            // System.out.println("Расширяю размер: " + size());
        }
        buffer.add(value);
    }

    public long size() {
        return buffer.size64();
    }

    public long capacity() {
        return BigArrays.length(buffer.elements()); // Текущая ёмкость
    }

    public byte[] toByteArray() {
        byte[] result = new byte[(int) size()];
        BigArrays.copyFromBig(buffer.elements(), 0, result, 0,(int) size());
        return result;
    }

}