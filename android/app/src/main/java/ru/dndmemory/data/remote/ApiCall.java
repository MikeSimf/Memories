package ru.dndmemory.data.remote;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;

/** Выполняет синхронный вызов Retrofit и приводит ошибки к {@link ApiException}. */
public final class ApiCall {

    private ApiCall() {
    }

    public static <T> T execute(Call<T> call) throws ApiException {
        try {
            Response<T> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            throw ApiException.fromResponse(response);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.network(e);
        } catch (RuntimeException e) {
            throw ApiException.unexpected(e);
        }
    }
}
