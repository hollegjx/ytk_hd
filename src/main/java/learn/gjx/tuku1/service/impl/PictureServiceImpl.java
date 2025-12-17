package learn.gjx.tuku1.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
* @author GJX
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-12-17 22:42:43
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

}




