<?php
//
// @(#)$HeadURL$ $Date$
//
// Copyright 1999-2013(c) MijnWinkel B.V. Rijnegomlaan 33, Aerdenhout,
// North Holland, NL-2114EH, The Netherlands All rights reserved.
//
// This software is the confidential and proprietary information of MijnWinkel
// B.V. ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the license
// agreement you entered into with MijnWinkel.
//
//
//
// Myshop RPC Client for retrieving productlist data and updating productlist data through the myshop RPC interface
//
// @version $Revision$ $Author$
// @since 08-10-2013
// @author Sem van der Wal
//
//

/**
 * Class MyshopRPCClient
 */
class MyshopRPCClient {

    private $privateKey = null;
    private $partnerName = null;

    private $tmpdir = TMP_DIR;

    /**
     * @param $partnerName String - The partner name for the current partner
     * @param $privateKey String - The private key for the current partner
     * @throws InvalidArgumentException - When arguments are empty or invalid
     */
    function __construct($partnerName, $privateKey){
        if($partnerName && $partnerName!="" && $privateKey && $privateKey!=""){
            $this->partnerName = $partnerName;
            $this->privateKey = $privateKey;
        }else{
            throw new InvalidArgumentException("Empty partnerName or privateKey supplied!");
        }
    }

    private function getResponse($url){
        $conn = curl_init($url);

        curl_setopt($conn, CURLOPT_RETURNTRANSFER,  true);
        curl_setopt($conn, CURLOPT_TIMEOUT,         10);
        curl_setopt($conn, CURLOPT_CONNECTTIMEOUT,  5);

        curl_setopt($conn, CURLOPT_SSL_VERIFYHOST, false);
        curl_setopt($conn, CURLOPT_SSL_VERIFYPEER, false);

        $result = curl_exec($conn);
        $status = curl_getinfo($conn, CURLINFO_HTTP_CODE);

        if($status==200 || !empty($result)){
            return $result;
        }else{
            return false;
        }
    }

    /**
     * Gets a Rpc Id from the RPC server
     * @param String $shopId The shopnumber of the current shop
     * @return String | NULL - The rpc id or null if the request was unsuccessfull
     */
    private function getRpcId($shopId){
        $url = 'http://local.rpc1-myshop.com/RPC/id?shop='.$shopId;
        $response = $this->getResponse($url);
        if($response==false){
            // Request failed, try again on different url
            $url = 'http://local.rpc2-myshop.com/RPC/id?shop='.$shopId;
            $response = $this->getResponse($url);
        }

        if($response && $response!=''){
            Logger::debug("Got RPCID Response: ".$response);
            // Got a response, now find and return the rpc_id
            $doc = new DOMDocument();
            $doc->loadXML($response);
            $rpcId = $doc->documentElement->firstChild->nodeValue;
            return $rpcId;
        }else{
            Logger::debug("Got no RPCID response!!");
            return null;
        }
    }

    /**
     * Gets response for a RPC command
     *
     * @param $command - The command / path to execute
     * @param $args - Associative array containing the variables to send
     * @return string - Returns the RPC response
     */
    private function getContents($command, $args){
        $url = 'http://local.rpc1-myshop.com/RPC/'.$command.'?'.http_build_query($args, '', '&');

        Logger::debug("URL: ".$url);

        $result = $this->getResponse($url);
        if($result==false){
            $url = 'http://local.rpc2-myshop.com/RPC/'.$command.'?'.http_build_query($args, '', '&');
            $result = $this->getResponse($url);
        }
        return $result;
    }

    private function uploadFile($command, $args, $name, $data){

        $url = 'http://local.rpc1-myshop.com/RPC/'.$command.'?'.http_build_query($args);
        $files = array(
            "userfile" => array(
                'name' => $name,
                'type' => 'application/zip',
                'content' => $data
            )
        );

        Logger::debug("Posting file to url: ".$url);

        return HttpPoster::post($url, null, $files);

    }

    /**
     * Gets a productlist from RPC.
     *
     * @param String $shopId - The shopnumber of the shop to get the productlist from
     * @param Integer $cid - The productlist id
     * @return String|null A XML string containing the productlist data or null on error
     */
    public function getProductlist($shopId, $cid){
        /*
        $name = substr($name, 0, strrpos($name, '.'));
        $name = $name.'.zip';
        */

        Logger::debug("Getting productlist: ".$shopId.'::'.$cid);

        $command = 'productlist/get/';
        $module = 'productlist_get';
        $rpcId = $this->getRpcId($shopId);
        $args = array(
            'pk' => sha1($shopId.$cid.$this->privateKey.$this->partnerName.$module.$rpcId),
            'productlist' => $cid,
            'shop' => $shopId,
            'rpc_id' => $rpcId,
            'partner' => $this->partnerName,
            'module' => $module,
            'all' => 'yes'
        );

        $zipContents = $this->getContents($command, $args);
        $tmpZip = fopen($this->tmpdir.'tmp.zip', 'w');
//        $tmpZip = tmpfile();
        if($tmpZip!=false){
            fwrite($tmpZip, $zipContents);
            fclose($tmpZip);
        }

        $zip = new ZipArchive();
        if($zip->open($this->tmpdir.'tmp.zip')==true){
            $xml = $zip->getFromIndex(0);
            if(empty($xml)){
                Logger::error("No zip contents: \n".file_get_contents($this->tmpdir.'tmp.zip'));
            }
            $zip->close();
            unlink($this->tmpdir.'tmp.zip');
            Logger::debug("Got productlist xml: \n".$xml);

            return $xml;
        }else{
            return null;
        }
    }

    /**
     * Gets productlist info from RPC.
     *
     * @param String $shopId - The shopnumber of the shop to get the productlist from
     * @param Integer $cid - The productlist id
     * @return string - A XML string containing the productlist meta data
     */
    public function getProductlistInfo($shopId, $cid){
        $command = 'productlist/info/';
        $module = 'productlist_get';
        $rpcId = $this->getRpcId($shopId);
        $args = array(
            'pk' => sha1($shopId.$cid.$this->privateKey.$this->partnerName.$module.$rpcId),
            'productlist' => $cid,
            'shop' => $shopId,
            'rpc_id' => $rpcId,
            'partner' => $this->partnerName,
            'module' => $module,
            'add_domain_collection' => 1
        );

        $contents =  $this->getContents($command, $args);
//        Logger::debug("Productlist info: \n".$contents);
        return $contents;
    }

    /**
     * @param $shopid String The shopid to upload the productlist to
     * @param $cid Integer The catalog id of the productlist to upload
     * @param $name String The name of the productlist to upload
     * @param $csvdata String The csv data of the productlist to upload
     *
     * @return bool True if the upload was successfull
     */
    public function uploadProductList($shopid, $cid, $name, $csvdata){

        $tmpFile = $this->tmpdir.$name.'.csv';
        file_put_contents($tmpFile, $csvdata);

        return $this->uploadProductListFile($shopid, $cid, $name, $tmpFile);

        $command = 'productlist/update';
        $module = 'productlist_update';
        $rpcId = $this->getRpcId($shopid);
        $filename = $name.'.zip';
        $args = array(
            'pk' => sha1($shopid.$cid.$this->privateKey.$this->partnerName.$module.$filename.$rpcId),
            'productlist' => $cid,
            'shop' => $shopid,
            'filename' => $filename,
            'rpc_id' => $rpcId,
            'partner' => $this->partnerName,
            'module' => $module
        );

        // Prepare zip file to upload
        $zip = new ZipArchive();
        $zip->open($this->tmpdir.$filename, ZipArchive::CREATE);
        $zip->addFromString($name.'.csv', $csvdata);
        $zip->close();

        $zipdata = file_get_contents($this->tmpdir.$filename);

        $result = $this->uploadFile($command, $args, $filename, $zipdata);

        Logger::debug("uploading ($shopid, $cid, $filename)");
        Logger::debug("result: $result");

        return strpos($result, "invalid request") === false;
    }

    /**
     * @param $shopid String The shopid to upload the productlist to
     * @param $cid Integer The catalog id of the productlist to upload
     * @param $name String The name of the productlist to upload
     * @param $filename String The filename of the productlistfile to upload
     *
     * @return bool True if the upload was successfull
     */
    public function uploadProductListFile($shopid, $cid, $name, $filename){

        $command = 'productlist/update';
        $module = 'productlist_update';
        $rpcId = $this->getRpcId($shopid);
        $tmpFilename = $name.'.zip';
        $args = array(
            'pk' => sha1($shopid.$cid.$this->privateKey.$this->partnerName.$module.$tmpFilename.$rpcId),
            'productlist' => $cid,
            'shop' => $shopid,
            'filename' => $tmpFilename,
            'rpc_id' => $rpcId,
            'partner' => $this->partnerName,
            'module' => $module
        );

        // Prepare zip file to upload
        $zip = new ZipArchive();
        $zip->open($this->tmpdir.$tmpFilename, ZipArchive::CREATE);
        $zip->addFile($filename, $name.'.csv');
        $zip->close();

        $zipdata = file_get_contents($this->tmpdir.$tmpFilename);

        Logger::debug("uploading ($shopid, $cid, $tmpFilename)");
        $result = $this->uploadFile($command, $args, $tmpFilename, $zipdata);
        Logger::debug("result: $result");

        return strpos($result, "invalid request") === false;
    }

    public function uploadDocumentZip($shopid, $name, $filename, $plugin=null, $extraArgs=null){
        /*
        public key=SHA1(<shop id>+<private partner key>+"document_import_myshop"+"import"+<document name>+<rpc id>)

 	    POST the document to https://www.mijnwinkel.nl/RPC/document/import?pk=<public key>&shop=<shop id>&filename=<document name>&rpc_id=<rpc id>&partner=document_import_myshop&module=import with
 	    the encryption type set to "multipart/form-data".

        Example at https://www.mijnwinkel.nl/views/rpc-test-documentimport-partner.html
         */

        $command = 'document/import';
        $module = 'import';
        $rpcId = $this->getRpcId($shopid);
        $tmpFilename = substr($name, 0, strrpos($name, '.')).'.zip';
        $args = array(
            'pk'            =>  sha1($shopid.$this->privateKey.$this->partnerName.$module.$tmpFilename.$rpcId),
            'shop'          =>  $shopid,
            'filename'      =>  $tmpFilename,
            'rpc_id'        =>  $rpcId,
            'partner'       =>  $this->partnerName,
            'module'        =>  $module
        );

        if($plugin!=null){
            $args['pluginname'] = $plugin;
        }
        if($args!=null){
            $args = array_merge($args, $extraArgs);
        }

        // Prepare zip file to upload
        $zip = new ZipArchive();
        $zip->open($this->tmpdir.$tmpFilename, ZipArchive::CREATE);
        $zip->addFile($filename, $name.'');
        $zip->close();

        $zipdata = file_get_contents($this->tmpdir.$tmpFilename);

        Logger::debug("uploading file ($shopid, $filename");
        $result = $this->uploadFile($command, $args, $tmpFilename, $zipdata);
        Logger::debug("result: \n$result");

        return strpos($result, "invalid request") === false;
    }

    public function updateStock($shopid, $productid, $stockvalue){

        $command = 'stock/update_n';
        $module = 'stock_update_n';
        $rpcId = $this->getRpcId($shopid);
        $args = array(
            'pk'            =>  sha1($shopid.$this->privateKey.$this->partnerName.$module.$productid.$stockvalue.$rpcId),
            'shop'          =>  $shopid,
            'productid0'    =>  $productid,
            'stock_value0'  =>  $stockvalue,
            'count'         =>  1,
            'rpc_id'        =>  $rpcId,
            'partner'       =>  $this->partnerName,
            'module'        =>  $module
        );

        $contents = $this->getContents($command, $args);

        return $contents;

    }

    /**
     * Send mail using myshop interface / shop
     * @param string $shopId
     * @param string $to
     * @param string $subject
     * @param string $body
     * @param string $from
     * @return string
     */
    public function sendMail($shopId, $to, $subject, $body, $from=''){

        $command = 'mail/';
        $module = 'send_mail';
        $rpcId = $this->getRpcId($shopId);
        $args = array(
            'pk'        =>  sha1($shopId.$this->privateKey.$this->partnerName.$module.$rpcId),
            'shop'      =>  $shopId,
            'to'        =>  $to,
            'subject'   =>  $subject,
            'body'      =>  $body,
            'rpc_id'    =>  $rpcId,
            'partner'   =>  $this->partnerName,
            'module'    =>  $module
        );
        if(!empty($from)){
            $args['from'] = $from;
        }

        $contents = $this->getContents($command, $args);
        Logger::debug("Result:\n".$contents);

        return $contents;

    }

    /**
     * Get information about a certain shop
     * @param string $shopId - The shopid to get info for
     * @param string[] $plugins - The plugin names to check
     * @return string - XML Containing shop info
     */
    public function getShopInfo($shopId, $plugins=array()){

        $command = 'shop/get/document.xml';
        $module = 'shop_info';
        $rpcId = $this->getRpcId($shopId);
        $args = array(
            'pk'        =>  sha1($shopId.$this->privateKey.$this->partnerName.$module.$rpcId),
            'shop'      =>  $shopId,
            'rpc_id'    =>  $rpcId,
            'partner'   =>  $this->partnerName,
            'module'    =>  $module
        );

        if(!empty($plugins)){
            $i = 0;
            foreach($plugins as $plugin){
                $args['plugin'.$i] = $plugin;
                $i ++;
            }
        }

        $contents = $this->getContents($command, $args);
        return $contents;

    }

    /**
     * Posts a file to an RPC command
     *
     * @param $command String - The command to execute
     * @param $args Array - Array of get variables to send
     * @param $filename String - The name of the file to send
     * @param $data String - The data to send
     * @return mixed
     */
    private function postImage($command, $args, $filename, $data){
        $url = 'http://local.rpc1-myshop.com/RPC/'.$command.'?'.http_build_query($args, '', '&');

        $ext = strtolower(substr($filename, strrpos($filename, '.')+1));
        $type = "";
        switch($ext){
            case "png":
                $type = "image/png";
                break;
            case "jpg":
                $type = "image/jpeg";
                break;
            case "gif":
                $type = "image/gif";
                break;
        }

        $result = HttpPoster::post($url, array(), array(
            "userfile" => array(
                "name" => $filename,
                "type" => $type,
                "content" => $data
            )
        ));

        if($result==false){
            // Fallback if rpc1 is not available
            $url = 'http://local.rpc2-myshop.com/RPC/'.$command.'?'.http_build_query($args, '', '&');
            $result = HttpPoster::post($url, array(), array(
                "userfile" => array(
                    "name" => $filename,
                    "type" => $type,
                    "content" => $data
                )
            ));
        }

        Logger::info("Posting to: ".$url);

        return $result;
    }

    /**
     * Stores an image to the myshop shop - myshop saves it to amazon S3
     *
     * @param $shopId Integer - The shop number of the shop where to upload the image
     * @param $imageName String - The name of the image
     * @param $imageData String - The image data to save
     * @return mixed The response from the myshop RPC server
     */
    public function storeCatalogImage($shopId, $imageName, $imageData){
        $command = "document/import";
        $module = "import";
        $rpcId = $this->getRpcId($shopId);
        $args = array(
            "pk" => sha1($shopId.$this->privateKey.$this->partnerName.$module.$imageName.$rpcId),
            "shop" => $shopId,
            "filename" => $imageName,
            "rpc_id" => $rpcId,
            "partner" => $this->partnerName,
            "module" => $module
        );
        return $this->postImage($command, $args, $imageName, $imageData);
    }

}

?>