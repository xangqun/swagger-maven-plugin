package com.wordnik.springmvc;

import com.wordnik.sample.data.ResponseDto;
import com.wordnik.sample.model.Pet;
import io.swagger.annotations.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.ws.rs.QueryParam;
import java.util.Map;

/**
 * @author carlosjgp
 */
@Api(value = "模型任务执行",description = "模型任务执行")
public class EmptyRootPathResource {
//    @ApiOperation(value = "testingEmptyRootPathResource",response = Map.class,responseContainer="ResponseDto")
    @ApiOperation(value = "提交文件同步任务",httpMethod = "POST",notes="提交文件同步任务")
//    @RequestMapping(value="/testingEmptyRootPathResource",method = RequestMethod.GET)
    @ApiImplicitParams({
            @ApiImplicitParam(value = "Pet object that needs to be added to the store",name = "pet",required = true,paramType="body")
    })
    //@ApiParam(name = "pet", value = "任务id", required = true)
    public ResponseDto testingEmptyRootPathResource( Pet<Object> pet) {
//                      @RequestBody
//            @ApiParam(value = "Pet object that needs to be added to the store",name = "pet",required = true)
//
        return new ResponseDto();
    }

}
