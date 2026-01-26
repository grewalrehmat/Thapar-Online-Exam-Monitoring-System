#include <iostream>
#include <vector>

using namespace std;

int binarySearch(vector<int>& nums,int val,int low,int high){
    if(low < high){
        int mid = (low+high) / 2;
        if(nums[mid] == val)return mid;
        if(nums[mid] > val) return binarySearch(nums,val,low,mid);
        else return binarySearch(nums,val,mid+1,high);
    }
    return -1;
}

int main(){
    vector<int> nums = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
    int ans = binarySearch(nums,23,0,nums.size() -1 );
    cout<<ans<<endl;
}