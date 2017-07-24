<?php

/**
 * @(#) myshopRequest.php 08/01/2013
 *
 * Copyright 1999-2013(c) MijnWinkel B.V. Rijnegomlaan 33, Aerdenhout,
 * North Holland, NL-2114EH, The Netherlands All rights reserved.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. MYSHOP AND
 * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES OR LIABILITIES
 * SUFFERED BY LICENSEE AS A RESULT OF  OR RELATING TO USE, MODIFICATION
 * OR DISTRIBUTION OF THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL
 * MYSHOP OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR
 * FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF MYSHOP HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any
 * nuclear facility.
 *
 *
 * Class MyshopRequest
 *
 * Reads the POST Body from the current request and provides access to the state and
 * parameter variables.
 *
 * This class was developed using PHP 5.3.6 with libxml.
 *
 * Version: 1.2
 * Author: Sem van der Wal
 **/

class MyshopRequest {

    private $root;
    private $params;
    private $state;
    private $modules = array();
    private $general = array();
    private $location;
    private $active;
    private $privateKey;

    function __construct($privateKey=null){
        if(empty($privateKey) && defined("MYSHOP_REQUEST_PRIVATE_KEY")){
            $privateKey = MYSHOP_REQUEST_PRIVATE_KEY;
        }
        if(!empty($privateKey)){
            $this->privateKey = $privateKey;

            $doc = new DOMDocument();
            $rbody = $this->getRequestBody();
            $ok = false;
            if(!empty($rbody)){
                $ok = $doc->loadXML($rbody);
                if($ok){
                    $this->root = $doc->documentElement;

                    $this->buildState();
                    $this->buildParams();
                    $this->buildGeneral();
                    $this->buildModules();

                    $this->setLocation();
                    $this->setActive();
                }else{
                    throw new MyshopXMLException();
                }
            }else{
                throw new MyshopEmptyRequestBodyException();
            }
//            if(!$this->checkSignature()){
//                 throw new MyshopSignatureException();
//            }
        }else{
            throw new InvalidArgumentException('Missing argument privateKey');
        }
    }

    /* Returns all states */
    public function getStates(){
        return $this->state;
    }

    /* Returns all parameters */
    public function getParams(){
        if($this->params){
            return $this->params;
        }else{
            error_log('Empty params');
            return array();
        }
    }

    /* Returns all general values */
    public function getGeneralValues(){
        return $this->general;
    }

    /* Returns specific named state variable */
    public function getState($name){
        if($this->state){
            if(isset($this->state[$name])){
                return $this->state[$name];
            }else{
                return '';
            }
        }else{
            error_log('Empty state');
            return '';
        }
    }

    /* Returns specific named parameter */
    public function getParam($name){
        if(isset($this->params[$name])){
            return $this->params[$name];
        }else{
            return '';
        }
    }

    /* Return module property value */
    public function getModuleProp($module, $name){
        if(isset($this->modules[$module]) && isset($this->modules[$module][$name])){
            return $this->modules[$module][$name];
        }else{
            return '';
        }
    }

    /* Returns location of the current plugin call */
    public function getLocation(){
        return $this->location;
    }

    /* Returns active state of the current plugin call */
    public function getActive(){
        return $this->active;
    }

    /* Checks if the given signature is the same as the expected one */
    private function checkSignature(){
        $signature = sha1($this->getState('application').'|'.$this->getRpcId().'|'.$this->getState('vid').'|'.$this->privateKey);
        if($signature != $this->getSignature()){
            Logger::debug("Got incorrect signature: ".$this->getSignature());
            Logger::debug("Expected signature: ".$signature);
            Logger::debug("Signature string: ".$this->getState('application').'|'.$this->getRpcId().'|'.$this->getState('vid').'|'.$this->privateKey);
        }
        return $signature == $this->getSignature();
    }

    /* Build the state array */
    private function buildState(){
        $this->state = array();
        try{
            $stateElements = $this->root->getElementsByTagName('state')->item(0)->childNodes;
            for($i=0;$i<$stateElements->length;$i++){
                $el = $stateElements->item($i);
                $this->state[$el->nodeName] = $el->nodeValue;
            }
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
        }
    }

    /* Build the params array */
    private function buildParams(){
        $this->params = array();
        try{
            $paramElements = $this->root->getElementsByTagName('request')->item(0)->getElementsByTagName('parameters')->item(0)->childNodes;
            for($i=0;$i<$paramElements->length;$i++){
                $el = $paramElements->item($i);
                if($el->nodeType==XML_ELEMENT_NODE){
                    $this->params[$el->getAttribute('name')] = $el->nodeValue;
                }
            }
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
        }
    }

    /* Build the general array */
    private function buildGeneral(){
        try{
            $paramElements = $this->root->getElementsByTagName('general')->item(0)->childNodes;
            for($i=0;$i<$paramElements->length;$i++){
                $el = $paramElements->item($i);
                if($el->nodeType==XML_ELEMENT_NODE){
                    $this->general[strtolower($el->getAttribute('id'))] = $el->nodeValue;
                }
            }
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
        }
    }

    /* Build the modules array */
    private function buildModules(){
        /*
        <modules>
            <module name=”import-state”>
                <property name=”articles”><!—count--></property>
            </module>
        </modules>
         */
        try{
            $moduleElements = $this->root->getElementsByTagName('module');
            for($i=0;$i<$moduleElements->length;$i++){
                $el = $moduleElements->item($i);
                if($el->nodeType==XML_ELEMENT_NODE){
                    /** @var DOMElement $el */
                    $moduleName = $el->getAttribute('name');
                    $this->modules[$moduleName] = array();
                    $propertyElements = $el->getElementsByTagName("property");
                    for($j=0;$j<$propertyElements->length;$j++){
                        $prop = $propertyElements->item($j);
                        $this->modules[$moduleName][$prop->getAttribute('name')] = $prop->nodeValue;
                    }
                }
            }
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
        }
    }

    /* Find and set the location of the current request in the myshop backoffice */
    private function setLocation(){
        try{
            $loc = $this->root->getElementsByTagName('request')->item(0)->getElementsByTagName('location');
            if($loc->length>0){
                $this->location = $loc->item(0)->nodeValue;
            }
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
        }
    }

    /* Find and set the plugin active state */
    private function setActive(){
        try{
            $active = $this->root->getElementsByTagName('request')->item(0)->getElementsByTagName('plugin_active');
            if($active->length>0){
                $this->active = $active->item(0)->nodeValue == '1';
            }
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
        }
    }

    /* Returns the rpc id, used to generate the signature */
    private function getRpcId(){
        try{
            return $this->root->getAttribute('rpc_id');
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
            return '';
        }
    }

    /* Returns the signature given by the server */
    private function getSignature(){
        try{
            return $this->root->getAttribute('signature');
        }catch(Exception $e){
            error_log('Encountered error while reading XML: '.$e->getMessage());
            return '';
        }
    }

    /* Returns the request body */
    private function getRequestBody(){
        if(isset($_REQUEST['request_info_xml'])){
            return $_REQUEST['request_info_xml'];
        }

        $req_body = '';
        $fh   = @fopen('php://input', 'r');
        if ($fh){
            while (!feof($fh)){
                $s = fread($fh, 1024);
                if (is_string($s)){
                    $req_body .= $s;
                }
            }
            fclose($fh);
        }
        return $req_body;
    }

}

/* Exception class for when the myshopRequest class is unable to read the xml from the request body */
class MyshopXMLException extends Exception {

    function __construct($msg='Unable to parse XML'){
        parent::__construct($msg);
    }

}

/* Exception class for when the request body has been found empty */
class MyshopEmptyRequestBodyException extends Exception {

    function __construct($msg='Request Body was empty'){
        parent::__construct($msg);
    }

}

/* Exception class for when the signature has been found invalid */
class MyshopSignatureException extends Exception {

    function __construct($msg='Unexpected signature found'){
        parent::__construct($msg);
    }

}

?>