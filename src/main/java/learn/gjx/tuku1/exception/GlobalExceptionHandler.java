package learn.gjx.tuku1.exception;


import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse handleBusinessException(BusinessException e){
        log.error(e.getMessage());
        return ResultUtils.error(e);
    }

}
