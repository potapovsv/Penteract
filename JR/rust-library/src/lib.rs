use std::ffi::c_int;

/// FFI-совместимая функция для суммирования всех элементов 4D куба
/// Принимает указатель на массив и общее количество элементов
/// Возвращает сумму как i64, чтобы избежать переполнения
#[no_mangle]
pub extern "C" fn sum_cube_elements(data: *const c_int, length: usize) -> i64 {
    // Проверяем, что указатель валиден
    if data.is_null() {
        return 0;
    }

    // Создаем срез из сырого указателя
    let slice = unsafe {
        std::slice::from_raw_parts(data, length)
    };

    // Вычисляем сумму всех элементов
    let sum: i64 = slice.iter().map(|&x| x as i64).sum();
    sum
}
