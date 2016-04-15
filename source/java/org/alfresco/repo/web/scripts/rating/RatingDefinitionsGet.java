package org.alfresco.repo.web.scripts.rating;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.service.cmr.rating.RatingScheme;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * This class is the controller class for the rating.definitions.get webscript.
 * 
 * @author Neil McErlean
 * @since 3.4
 */
public class RatingDefinitionsGet extends AbstractRatingWebScript
{
    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        Map<String, RatingScheme> schemes = this.ratingService.getRatingSchemes();
        
        model.put("schemeDefs", schemes);

        return model;
    }
}
