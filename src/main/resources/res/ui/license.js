/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var licenseButtons = false;
var licenseDetails = false;

function displayBundle(/* String */ bundleId)
{
    var theBundleData = bundleData[bundleId];
    if (!theBundleData)
    {
        return;
    }

    var title = theBundleData.title;
    
    if (licenseButtons) {
        
        var innerHTML = "";
        for (var name in theBundleData.files)
        {
            var entry = theBundleData.files[name];
            var buttons = "";
            var firstPage = null;
            for (var idx in entry)
            {
                var descr = entry[idx];
				var jar = descr.jar ? '&jar=' + descr.jar : ''; // inner jar attribute
				var link = pluginRoot + '?bid=' + bundleId + '&url=' + descr.path + jar;
				buttons += '<a href="' + link + '">' + descr.url + '</a> ';
				if (!firstPage) {
				    firstPage = link;
				}
            }
            if (buttons)
            {
				// apply i18n
				name =  '__res__' == name ? i18n.resources : i18n.resources_emb.msgFormat( name );
                innerHTML += name + ": " + buttons + "<br />";
            }
        }

        licenseButtons.html("<h1>" + title + "</h1>" + innerHTML);
    }
    
    if (firstPage) {
        licenseDetails.load(firstPage);
    } else {
        licenseDetails.html("");
    }
    
	$("#licenseLeft a").removeClass('ui-state-default ui-corner-all');
	$("#licenseLeft #" +bundleId).addClass('ui-state-default ui-corner-all');

    $('#licenseButtons a').click(function() {
       licenseDetails.load(this.href);
       return false;
    });
}


$(document).ready(function() {
	// init elements cache
	licenseButtons = $("#licenseButtons");
	licenseDetails = $("#licenseDetails")

	// render list of bundles
	var txt = "";
	for(id in bundleData) {
		txt += '<a id="' + id + '" href="javascript:displayBundle(\'' + id + '\')">' + bundleData[id]['title'] + '</a>';
	}
	if (txt) {
		$("#licenseLeft").html(txt);
	} else {
		$(".statline").html(i18n.status_none);
	}

	// display first element
	for(i in bundleData) {displayBundle(i);break;}
});